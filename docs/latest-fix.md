# Latest Fix

This note explains the most recent repair pass across the upload pipeline, why the bugs mattered, and how the code behaves now.

## What was broken

The most important bug was in the handoff from the Java API into the first Python worker.

Before the fix:

- the API created a new job as `UPLOAD / QUEUED`
- the normalization worker expected to process `NORMALIZATION`
- the worker idempotency guard only allowed:
  - same-stage retries like `NORMALIZATION / QUEUED`
  - or a previously completed stage with loose conditions

That meant a freshly uploaded file could be published to RabbitMQ, consumed by the normalization worker, and then incorrectly treated as a duplicate or out-of-order message. In practice, the message could be ACKed without normalization actually running.

There were also two related problems:

- Python workers were publishing event types like `AUDIO_NORMALIZED` while Java used dot-style names like `audio.uploaded`
- local Compose infrastructure did not include PostgreSQL even though both the API and workers defaulted to PostgreSQL

There was one smaller API issue too:

- `GET /api/files/{objectKey}` was awkward for nested object keys like `normalized/123.mp3`

## What changed

### 1. Job creation now starts at the first real worker stage

In [AudioFileService.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/service/AudioFileService.java:77), newly created jobs now start as:

- `NORMALIZATION / QUEUED`

This matches the actual next consumer after upload.

Why this matters:

- the database state now reflects the next executable stage
- the normalization worker no longer receives a job whose state looks unrelated to its purpose

### 2. Worker stage progression is now explicit

In [worker_base.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/worker_base.py:20) and [worker_base.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/worker_base.py:313), the idempotency logic was changed from a loose check to an ordered stage model.

The worker now knows this stage sequence:

- `UPLOAD`
- `NORMALIZATION`
- `TRANSCRIPTION`
- `EMBEDDING`

The new rules are:

- process if the job is already at the expected stage and is `QUEUED` or `FAILED`
- process if the immediately previous stage is `COMPLETED` and the worker is the legitimate next stage
- otherwise skip as duplicate or out-of-order

Why this matters:

- normalization can process fresh jobs
- transcription can process only after normalization completes
- stale or misrouted messages are less likely to mutate job state incorrectly

### 3. Python event metadata now matches the Java event contract more closely

In [normalization_worker.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/normalization_worker.py:120), [transcription_worker.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/transcription_worker.py:163), and [worker_base.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/worker_base.py:286):

- `AUDIO_NORMALIZED` became `audio.normalized`
- `AUDIO_TRANSCRIBED` became `audio.transcribed`
- `eventVersion` changed from `1.0` to `1`

Why this matters:

- event naming is now more consistent across Java and Python
- downstream inspection and debugging are simpler because event shape is less mixed

This does not fully remove cross-language contract drift, but it reduces it.

### 4. Local Compose now includes PostgreSQL

In [docker-compose.yml](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/docker-compose.yml:1) and [start-pipeline.sh](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/scripts/start-pipeline.sh:67):

- PostgreSQL was added as a Compose service
- startup health checks now include PostgreSQL
- the obsolete Compose `version` field was removed

In [workers/.env.example](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/.env.example:1):

- the default worker DB URL was changed from a remote Neon connection string to local PostgreSQL
- `UPLOADED_ROUTING_KEY` was added explicitly to worker config

Why this matters:

- the repo now matches its own default local-development story more closely
- a fresh local setup no longer depends on an external database by default

### 5. File downloads now support nested MinIO keys

In [FileController.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/controller/FileController.java:43), the download route now uses a wildcard path variable:

- `/api/files/{*objectKey}`

Why this matters:

- keys like `normalized/123.mp3` and `transcripts/123.txt` can be addressed through the route
- the API now better matches how objects are actually stored

### 6. The broken duplicate test stub was replaced

The old file at:

- `AudioPipeline/src/test/java/com/AudioPipeline/AudioPipeline/ApplicationTests.java`

had a package/path mismatch and caused Maven test failures unrelated to the actual fix.

It was replaced with:

- [ApplicationTests.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/test/java/com/AudioPipeline/ApplicationTests.java:1)

Why this matters:

- `mvn test` can validate the repo again without failing on a bogus test class layout

## End-to-end flow after the fix

The intended flow now looks like this:

1. `POST /api/upload` stores the raw file in MinIO.
2. The API creates a job as `NORMALIZATION / QUEUED`.
3. The API publishes `audio.uploaded`.
4. The normalization worker receives the message and accepts it as valid work.
5. The worker marks the job `NORMALIZATION / PROCESSING`.
6. The worker downloads the raw object, normalizes it, uploads the normalized file, then marks the job `NORMALIZATION / COMPLETED`.
7. The worker publishes `audio.normalized`.
8. The transcription worker receives that event, sees that the previous stage completed, and accepts the handoff.
9. The transcription worker marks the job `TRANSCRIPTION / PROCESSING`, generates a transcript, saves it, and marks the job `TRANSCRIPTION / COMPLETED`.
10. The worker publishes `audio.transcribed`.

That flow is much closer to what the architecture docs imply the system was trying to do.

## What this fix did not solve

This repair pass was targeted. It did not address everything in the issues list.

Still open:

- Java and Python still both define RabbitMQ topology, so drift is still possible
- `AudioFileService` still owns too many responsibilities
- worker retry behavior is still simplistic
- the embedding stage still appears only partially implemented
- the startup scripts still have rough edges beyond the PostgreSQL gap

## How it was verified

The fix was checked with:

- Python syntax validation for the worker files
- `docker compose config`
- `./AudioPipeline/mvnw -f AudioPipeline/pom.xml clean test`

At the time of this note, those checks passed after the test stub was corrected.
