# RabbitMQ Full Working Flow

This document reflects the implemented flow in the Spring app, aligned with the async direction in `context.md`, `documentation.md`, and `todo.md`.

## 1) Queue Topology

- Exchange: `audio.processing.exchange` (topic)
- Dead-letter exchange: `audio.processing.dlx` (direct)
- Stage queues:
  - `audio.normalization.queue`
  - `audio.transcription.queue`
  - `audio.embedding.queue`
- DLQs:
  - `audio.normalization.dlq`
  - `audio.transcription.dlq`
  - `audio.embedding.dlq`
- Routing keys:
  - `audio.uploaded`
  - `audio.normalized`
  - `audio.transcribed`
  - `audio.embedded`

All queue/exchange/routing values are configurable via `app.rabbitmq.*` in `AudioPipeline/src/main/resources/application.yaml`.

## 2) Event Contract (Implemented)

Envelope (`AudioPipelineEvent`):

- `eventType`
- `eventVersion`
- `eventId`
- `occurredAt`
- `traceId`
- `payload`

Payload (`AudioStagePayload`):

- `audioFileId`
- `jobId`
- `objectKey`

## 3) End-to-End Runtime Flow

### Step A - Upload API publishes `audio.uploaded`

Endpoint: `POST /api/upload`

Behavior:

1. Validates multipart file.
2. Stores object in MinIO.
3. Persists upload metadata in `audiofile`.
4. Creates job in `audio_processing_jobs` with:
   - `stage=UPLOAD`
   - `status=QUEUED`
   - `traceId=<uuid>`
5. Publishes `audio.uploaded` event.
6. Returns response with:
   - `audioId`
   - `jobId`
   - `filePath`
   - `stage`
   - `status`
   - `traceId`

### Step B - Normalization listener processes upload

Listener: `onUploaded` on queue `${app.rabbitmq.normalization-queue}`

Behavior:

1. Consumes `audio.uploaded`.
2. Loads job row by `jobId`.
3. Updates stage/status to `NORMALIZATION/PROCESSING` then `COMPLETED`.
4. Updates object key to `normalized/<originalObjectKey>`.
5. Publishes `audio.normalized`.

### Step C - Transcription listener processes normalized event

Listener: `onNormalized` on queue `${app.rabbitmq.transcription-queue}`

Behavior:

1. Consumes `audio.normalized`.
2. Loads job row by `jobId`.
3. Updates stage/status to `TRANSCRIPTION/PROCESSING` then `COMPLETED`.
4. Updates object key to `transcripts/<audioFileId>.txt`.
5. Publishes `audio.transcribed`.

### Step D - Embedding listener finalizes pipeline

Listener: `onTranscribed` on queue `${app.rabbitmq.embedding-queue}`

Behavior:

1. Consumes `audio.transcribed`.
2. Loads job row by `jobId`.
3. Updates stage/status to `EMBEDDING/COMPLETED`.
4. Sets `completedAt`.
5. Publishes `audio.embedded` (terminal event).

## 4) Job Status API (Implemented)

Endpoint: `GET /api/jobs/{jobId}`

Returns current processing state from `audio_processing_jobs`, including:

- `stage`
- `status`
- `retryCount`
- `objectKey`
- `traceId`
- `errorCode`
- `errorMessage`
- timestamps (`createdAt`, `startedAt`, `completedAt`, `updatedAt`)

## 5) Key Implemented Classes

- Queue config:
  - `com.AudioPipeline.Configuration.RabbitMqConfig`
  - `com.AudioPipeline.Configuration.RabbitMqProperties`
- Event + status DTOs:
  - `com.AudioPipeline.dto.AudioPipelineEvent`
  - `com.AudioPipeline.dto.AudioStagePayload`
  - `com.AudioPipeline.dto.AudioJobStatusDto`
  - `com.AudioPipeline.dto.AudioFileDto` (extended upload response)
- Publisher:
  - `com.AudioPipeline.service.AudioEventPublisher`
- Processing persistence:
  - `com.AudioPipeline.entity.AudioProcessingJobEntity`
  - `com.AudioPipeline.repository.AudioProcessingJobRepository`
- Orchestration + listeners:
  - `com.AudioPipeline.service.AudioFileService`
- API endpoints:
  - `com.AudioPipeline.controller.FileController`

## 6) Run and Verify

1. Ensure infra is up (RabbitMQ, PostgreSQL, MinIO) and env variables are set.
2. Start app:
   - `./AudioPipeline/mvnw -f AudioPipeline/pom.xml spring-boot:run`
3. Upload a file:
   - `POST /api/upload` with multipart field `file`
4. Poll job:
   - `GET /api/jobs/{jobId}`
5. Observe stage progression toward `EMBEDDING / COMPLETED`.

## 7) Current Gap vs Todo Reliability Targets

Core event flow is implemented and working in-app.  
Remaining hardening from `todo.md` (not yet implemented) includes:

- bounded retry/backoff policies
- explicit transient/permanent error classification
- idempotency guards for redelivery
- DLQ reason metadata and poison-message handling
- transcript persistence table and API retrieval endpoint
