# API Lifecycle

This document traces what happens for one upload request from the first HTTP byte to the final transcript record.

It is based on the current code in:

- `AudioPipeline/src/main/java/com/AudioPipeline/controller/FileController.java`
- `AudioPipeline/src/main/java/com/AudioPipeline/service/AudioFileService.java`
- `AudioPipeline/src/main/java/com/AudioPipeline/service/AudioEventPublisher.java`
- `AudioPipeline/src/main/java/com/AudioPipeline/Configuration/RabbitMqConfig.java`
- `workers/worker_base.py`
- `workers/normalization_worker.py`
- `workers/transcription_worker.py`

## System Pieces Involved

One request crosses five subsystems:

1. Spring Boot HTTP API
2. MinIO object storage
3. PostgreSQL
4. RabbitMQ
5. Python workers

The pipeline is event-driven after the initial upload, but the request is not fully lightweight because the API still uploads the raw file to MinIO before returning.

## End-to-End Flow

### 1. Client sends `POST /api/upload`

The request enters `FileController.uploadFile()` with a multipart file.

Code path:

- `FileController.uploadFile(...)`
- `AudioFileService.uploadFile(...)`

What the controller does:

- reads the uploaded multipart file
- extracts the caller IP with `request.getRemoteAddr()`
- delegates all real work to `AudioFileService`
- rewrites the relative status URL into an absolute URL
- returns `202 Accepted`

Important detail:

- the controller returns `202`, but only after storage and database work already happened

### 2. API validates the file

`AudioFileService.uploadFile()` immediately checks:

- file is not `null`
- file is not empty

If validation fails, it throws `IllegalArgumentException`.

### 3. API ensures the MinIO bucket exists

Before writing the file, `ensureBucketExists()` runs:

- `bucketExists(...)`
- if missing, `makeBucket(...)`

This happens inside the request path for every upload.

### 4. API stores the raw audio in MinIO

The service builds an object key like:

`<uuid>-<sanitized-original-filename>`

Then it streams the multipart body into MinIO with `minioClient.putObject(...)`.

At this point:

- the raw file is already persisted in object storage
- the client still has not received a response

### 5. API writes the `audiofile` row

After the MinIO upload succeeds, the API creates `AudioFilesEntity` and saves:

- `ip_address`
- `file_path`
- `created_at`

This row represents the uploaded source file.

### 6. API creates the processing job row

The API generates a new `traceId` and inserts `audio_processing_jobs` with:

- `audio_file_id`
- `object_key` = raw MinIO key
- `stage` = `NORMALIZATION`
- `status` = `QUEUED`
- `retry_count` = `0`
- `trace_id` = generated UUID

This row is the durable state machine for the pipeline.

### 7. API publishes `audio.uploaded`

`AudioEventPublisher.publishUploadedEvent(...)` sends a JSON message to RabbitMQ:

- exchange: `audio.processing.exchange`
- routing key: `audio.uploaded`

Payload:

- `audioFileId`
- `jobId`
- `objectKey`

Envelope:

- `eventType`
- `eventVersion`
- `eventId`
- `occurredAt`
- `traceId`

RabbitMQ routes this to:

- queue: `audio.normalization.queue`

via the binding configured in `RabbitMqConfig`.

### 8. API returns `202 Accepted`

The response body contains:

- `audioId`
- `jobId`
- `filePath`
- `stage`
- `status`
- `traceId`
- `statusUrl`

At response time, the normal state is:

- file is in MinIO
- `audiofile` row exists
- job row exists
- `audio.uploaded` has been published
- no background processing has completed yet

## Background Processing

### 9. Normalization worker consumes `audio.uploaded`

`NormalizationWorker` listens on:

- `audio.normalization.queue`

`WorkerBase.handle_message(...)` does the generic RabbitMQ work:

- parse JSON body
- pull `traceId`
- call `process_message(...)`
- `ACK` only on success
- `NACK requeue=True` on failure

### 10. Normalization worker checks idempotency

`check_idempotency(job_id, "NORMALIZATION", trace_id)` reads:

- current `stage`
- current `status`

It allows processing when either:

- job is exactly `NORMALIZATION/QUEUED` or `NORMALIZATION/FAILED`
- job is one stage behind but already `COMPLETED`

That is meant to suppress duplicates and out-of-order messages.

### 11. Normalization worker marks the job `PROCESSING`

It updates `audio_processing_jobs`:

- `stage` = `NORMALIZATION`
- `status` = `PROCESSING`
- `started_at` = `CURRENT_TIMESTAMP`
- `updated_at` = `CURRENT_TIMESTAMP`

### 12. Normalization worker downloads raw audio from MinIO

The worker creates a temp file and downloads the raw object key from MinIO into local disk.

If download fails:

- job becomes `FAILED`
- `error_code` = `DOWNLOAD_FAILED`
- message is `NACK`ed and requeued

### 13. Normalization worker runs FFmpeg

`normalize_audio(...)` applies FFmpeg `loudnorm` and writes a new temp MP3.

Target settings:

- `I=-16`
- `TP=-1.5`
- `LRA=11`
- output codec `libmp3lame`
- bitrate `192k`
- sample rate `44100`

If FFmpeg fails:

- job becomes `FAILED`
- `error_code` = `NORMALIZATION_FAILED`
- message is requeued

### 14. Normalization worker uploads normalized audio

The worker writes normalized output back to MinIO as:

`normalized/<audioFileId>.mp3`

If upload fails:

- job becomes `FAILED`
- `error_code` = `UPLOAD_FAILED`
- message is requeued

### 15. Normalization worker marks the stage complete

If normalization succeeded, the job row is updated to:

- `stage` = `NORMALIZATION`
- `status` = `COMPLETED`
- `object_key` = `normalized/<audioFileId>.mp3`
- `completed_at` = `CURRENT_TIMESTAMP`

### 16. Normalization worker publishes `audio.normalized`

It publishes the next event with routing key:

- `audio.normalized`

RabbitMQ routes this to:

- `audio.transcription.queue`

Then the original `audio.uploaded` message is `ACK`ed.

### 17. Transcription worker consumes `audio.normalized`

`TranscriptionWorker` listens on:

- `audio.transcription.queue`

It follows the same base flow:

- parse event
- idempotency check
- update job state
- work on local temp files
- `ACK` or `NACK`

### 18. Transcription worker marks transcription `PROCESSING`

The job row becomes:

- `stage` = `TRANSCRIPTION`
- `status` = `PROCESSING`

### 19. Transcription worker downloads normalized audio

It downloads:

`normalized/<audioFileId>.mp3`

from MinIO into a temp file.

If this fails:

- job becomes `FAILED`
- `error_code` = `DOWNLOAD_FAILED`
- message is requeued

### 20. Transcription worker loads Whisper and transcribes audio

The worker loads a Whisper model at startup:

- default model: `base`

For each message it calls `model.transcribe(...)` and extracts:

- transcript text
- language
- segment metadata

### 21. Transcription worker writes transcript to PostgreSQL

It inserts one row into `transcripts`:

- `audio_file_id`
- `job_id`
- `transcript_text`
- `language`
- `confidence`
- `created_at`

Confidence is derived from Whisper segment `no_speech_prob`.

### 22. Transcription worker marks the job complete

It updates `audio_processing_jobs` to:

- `stage` = `TRANSCRIPTION`
- `status` = `COMPLETED`
- `object_key` = `transcripts/<audioFileId>.txt`

### 23. Transcription worker publishes `audio.transcribed`

It publishes:

- exchange: `audio.processing.exchange`
- routing key: `audio.transcribed`

RabbitMQ routes this to:

- `audio.embedding.queue`

There is currently no embedding worker in the repository, so the event stops there.

### 24. Client can poll job status

`GET /api/jobs/{jobId}` reads the current row from `audio_processing_jobs` and returns:

- current `stage`
- current `status`
- `objectKey`
- timestamps
- error fields
- `traceId`

This is the main tracking endpoint for the full async pipeline.

### 25. Client can fetch transcript

`GET /api/audio/{audioId}/transcript` queries the `transcripts` table by `audio_file_id`.

If a transcript row exists, the API returns:

- transcript text
- language
- confidence

If not, it returns `404`.

## Queue Topology

RabbitMQ is configured with one topic exchange and one dead-letter exchange.

Main exchange:

- `audio.processing.exchange`

Dead-letter exchange:

- `audio.processing.dlx`

Main route chain:

1. `audio.uploaded` -> `audio.normalization.queue`
2. `audio.normalized` -> `audio.transcription.queue`
3. `audio.transcribed` -> `audio.embedding.queue`

Dead-letter queues exist for:

- normalization
- transcription
- embedding

## What Really Happens Versus What The Design Suggests

This section is the important part.

### The request is only partially asynchronous

The API responds with `202 Accepted`, but the request thread still does three expensive things before returning:

1. bucket existence check
2. raw file upload to MinIO
3. two database inserts

So the user is not waiting for normalization or transcription, but they are still waiting for object storage I/O.

### The event publish is not transactionally coupled to the database commit

`AudioFileService.uploadFile()` is `@Transactional`, but the RabbitMQ publish happens inside that method before the transaction commits.

That means the sequence is:

1. save entity
2. save job
3. publish RabbitMQ event
4. method returns
5. Spring commits DB transaction

If commit fails after publish, RabbitMQ could contain an event whose job row never committed.

### MinIO is outside the transaction boundary

If the raw file upload succeeds but the database insert later fails:

- the object stays in MinIO
- the API transaction rolls back
- no database record points to that object

That leaves orphaned storage.

### Retry state is incomplete

`WorkerBase` defines:

- `max_retries`
- `retry_backoff`
- `retry_count` column

But the current code does not actually:

- increment `retry_count`
- enforce `max_retries`
- apply backoff timing

In practice, worker failure leads to `basic_nack(requeue=True)` and immediate requeue attempts.

### Failed jobs can loop forever

Because failures are requeued immediately and there is no retry ceiling in the current logic:

- transient failures retry forever
- permanent failures also retry forever

The DLQs are declared, but this code path does not explicitly route messages there after a retry threshold.

### The final transcription `object_key` does not point to a real uploaded object

After transcription, the job is marked with:

`object_key = transcripts/<audioFileId>.txt`

But the worker does not upload any transcript file to MinIO.
It only inserts a transcript row into PostgreSQL.

So the final job status suggests a transcript object exists in storage, but in the current implementation the transcript lives only in the database.

### The pipeline ends at an unimplemented embedding stage

`audio.transcribed` is published and RabbitMQ is configured with:

- `audio.embedding.queue`

But there is no embedding worker in this repository.

So the job state machine reaches `TRANSCRIPTION/COMPLETED`, then emits a follow-up event that nothing consumes.

### `started_at` is stage-scoped, not job-scoped

Every time a worker marks `PROCESSING`, `started_at` is set again.

So `started_at` effectively means:

- start time of the current stage

not:

- first time the whole job began

## One Concrete Trace

Example lifecycle for one successful upload:

1. Client sends `POST /api/upload` with `song.mp3`
2. API stores raw file in MinIO as `b1f...-song.mp3`
3. API inserts `audiofile`
4. API inserts `audio_processing_jobs` with `NORMALIZATION/QUEUED`
5. API publishes `audio.uploaded`
6. API returns `202 Accepted`
7. Normalization worker consumes the message
8. Job becomes `NORMALIZATION/PROCESSING`
9. Worker downloads `b1f...-song.mp3`
10. Worker creates normalized audio
11. Worker uploads `normalized/<audioId>.mp3`
12. Job becomes `NORMALIZATION/COMPLETED`
13. Worker publishes `audio.normalized`
14. Transcription worker consumes that event
15. Job becomes `TRANSCRIPTION/PROCESSING`
16. Worker downloads `normalized/<audioId>.mp3`
17. Whisper generates transcript text
18. Worker inserts transcript row into PostgreSQL
19. Job becomes `TRANSCRIPTION/COMPLETED`
20. Worker publishes `audio.transcribed`
21. Event lands in `audio.embedding.queue`
22. Client polls `GET /api/jobs/{jobId}`
23. Client fetches `GET /api/audio/{audioId}/transcript`

## How To Observe A Single Request Live

To trace one request end to end in a running system, follow these IDs:

- `jobId`
- `audioId`
- `traceId`
- MinIO `objectKey`
- RabbitMQ routing keys

Best observation points:

1. HTTP response from `POST /api/upload`
2. `audio_processing_jobs` row for the returned `jobId`
3. worker logs filtered by `traceId`
4. RabbitMQ queues:
   - `audio.normalization.queue`
   - `audio.transcription.queue`
   - `audio.embedding.queue`
5. `transcripts` row for the returned `audioId`

## Bottom Line

The current request lifecycle is:

`HTTP upload -> MinIO raw object -> DB metadata/job row -> RabbitMQ event -> normalization worker -> MinIO normalized object -> transcription worker -> transcript row in PostgreSQL -> final event to unused embedding queue`

The critical practical nuance is that the system is asynchronous only after the raw upload and initial database writes complete.
