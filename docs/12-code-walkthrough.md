# Code Walkthrough

This walkthrough traces one real request: `POST /api/upload`.

Related docs:

- [Request Flow](./02-request-flow.md)
- [API Reference](./04-api-reference.md)

## Scenario

Client sends:

```bash
curl -X POST http://localhost:8080/api/upload -F "file=@sample.mp3"
```

## Step 1: Spring Boot starts the web app

Entry point:

- [`AudioPipeline/src/main/java/com/AudioPipeline/Application.java`](../AudioPipeline/src/main/java/com/AudioPipeline/Application.java)

What matters:

- `@SpringBootApplication` triggers component scanning
- Spring discovers controller, service, repositories, and configuration beans

## Step 2: The request is routed to `FileController.uploadFile`

Source:

- [`FileController`](../AudioPipeline/src/main/java/com/AudioPipeline/controller/FileController.java)

Key behavior:

1. Spring maps `POST /api/upload`.
2. Spring binds multipart field `file` to `MultipartFile`.
3. Controller reads client IP with `request.getRemoteAddr()`.
4. Controller calls `audioFileService.uploadFile(file, ip)`.

Why this code exists:

- keep HTTP concerns at the boundary
- pass only business-relevant inputs into the service

## Step 3: `AudioFileService.uploadFile` validates the file

Source:

- [`AudioFileService`](../AudioPipeline/src/main/java/com/AudioPipeline/service/AudioFileService.java)

The service first checks:

- `file == null`
- `file.isEmpty()`

If either is true, it throws `IllegalArgumentException("File is required")`.

Why:

- prevent storage/database work on invalid requests

## Step 4: The service ensures the MinIO bucket exists

Method:

- `ensureBucketExists()`

What it does:

1. calls `minioClient.bucketExists(...)`
2. if false, calls `minioClient.makeBucket(...)`

Why:

- bootstrap convenience for local environments

Tradeoff:

- bucket existence check runs on every upload request

## Step 5: The service creates the object key

Method:

- `buildObjectName(originalFilename)`

What it does:

1. replaces non-safe filename characters with `_`
2. prepends a random UUID

Result shape:

- `uuid-original_name.mp3`

Why:

- avoid collisions
- reduce storage key injection issues

## Step 6: The raw file is uploaded to MinIO

Still inside `uploadFile(...)`:

1. service opens `file.getInputStream()`
2. calls `minioClient.putObject(...)`
3. sends stream, size, and content type

Why:

- store large binary payloads outside the database

## Step 7: Upload metadata is persisted

Entity:

- [`AudioFilesEntity`](../AudioPipeline/src/main/java/com/AudioPipeline/entity/AudioFilesEntity.java)

What is written:

- `ipAddress`
- `filePath`

How:

- `audioFileRepository.save(audioFilesEntity)`

Why:

- capture provenance and the original object key

## Step 8: A processing job row is created

Entity:

- [`AudioProcessingJobEntity`](../AudioPipeline/src/main/java/com/AudioPipeline/entity/AudioProcessingJobEntity.java)

Fields set:

- `audioFileId`
- `objectKey`
- `stage = "UPLOAD"`
- `status = "QUEUED"`
- `retryCount = 0`
- `traceId = UUID.randomUUID().toString()`

How:

- `jobRepository.save(job)`

Why:

- async pipeline needs a durable handle clients can poll

## Step 9: The upload event is published

Publisher:

- [`AudioEventPublisher`](../AudioPipeline/src/main/java/com/AudioPipeline/service/AudioEventPublisher.java)

What happens:

1. `publishUploadedEvent(...)` is called
2. publisher builds `AudioPipelineEvent`
3. payload becomes `AudioStagePayload(audioFileId, jobId, objectKey)`
4. `rabbitTemplate.convertAndSend(exchange, routingKey, event)`

Routing key:

- defaults to `audio.uploaded`

Why:

- decouple heavy work from the request thread

## Step 10: Service returns the upload DTO

DTO:

- [`AudioFileDto`](../AudioPipeline/src/main/java/com/AudioPipeline/dto/AudioFileDto.java)

Fields:

- `audioId`
- `jobId`
- `filePath`
- `stage`
- `status`
- `traceId`
- `statusUrl` as relative path `/api/jobs/{jobId}`

## Step 11: Controller rewrites the status URL

Back in `FileController.uploadFile(...)`:

1. build absolute URL using `ServletUriComponentsBuilder.fromContextPath(request)`
2. replace DTO `statusUrl`
3. return `ResponseEntity.status(HttpStatus.ACCEPTED).body(saved)`

Why:

- client gets a directly pollable URL

## Step 12: RabbitMQ delivers the event to the normalization worker

Worker files:

- [`worker_base.py`](../workers/worker_base.py)
- [`normalization_worker.py`](../workers/normalization_worker.py)

Flow:

1. worker consumes from `audio.normalization.queue`
2. decodes JSON event
3. extracts `traceId` and payload
4. runs `process_message`

## Step 13: The normalization worker tries the idempotency check

Code path:

- `WorkerBase.check_idempotency(job_id, "NORMALIZATION", trace_id)`

Current logic:

- returns `true` if current status is `COMPLETED` and stage is not expected stage
- returns `true` if current stage is expected stage and status in `QUEUED`/`FAILED`
- otherwise logs duplicate and returns `false`

Observed result from [`workers/normalization_worker.log`](../workers/normalization_worker.log):

- job `1` at `UPLOAD/QUEUED` was treated as duplicate and skipped

Why this matters:

- the intended first-stage transition does not satisfy the current condition
- this is likely a bug, not intended business behavior

## Step 14: Intended normalization path

If the guard were passed, the worker would:

1. set job to `NORMALIZATION/PROCESSING`
2. download raw audio from MinIO
3. run FFmpeg loudnorm
4. upload normalized file to `normalized/{audioFileId}.mp3`
5. set job to `NORMALIZATION/COMPLETED`
6. publish follow-up event

## Step 15: Intended transcription path

The transcription worker would then:

1. consume the normalized event
2. set job to `TRANSCRIPTION/PROCESSING`
3. download normalized audio
4. run Whisper transcription
5. insert transcript row
6. set job to `TRANSCRIPTION/COMPLETED`
7. publish another event

## What This Walkthrough Shows

The architecture itself is straightforward:

- HTTP ingress in Java
- async work in Python
- shared state in PostgreSQL
- artifacts in MinIO
- sequencing in RabbitMQ

The main implementation risk is not conceptual complexity. It is cross-language contract drift and small logic bugs in the handoff points.
