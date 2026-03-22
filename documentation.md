# Technical Flow Notes - Async Audio Pipeline

## 1) Request Path (API)

Client uploads audio to API endpoint.

API responsibilities are intentionally lightweight:

- validate file type/size
- extract request metadata (for example client IP)
- store raw audio object in MinIO/S3-compatible storage
- persist upload metadata in PostgreSQL
- create processing job row
- publish `audio.uploaded` event
- return quickly with `audioId`, `jobId`, and queued status

No heavy audio processing should run in API request threads.

---

## 2) Queue and Event Flow

RabbitMQ topic exchange:

- `audio.processing.exchange`

Primary routing keys:

- `audio.uploaded`
- `audio.normalized`
- `audio.transcribed`
- `audio.embedded`

Stage workers consume from dedicated queues and publish the next stage event.

---

## 3) Worker Responsibilities

Normalization worker:

- consume `audio.uploaded`
- download raw object from storage
- run FFmpeg normalization/format policy
- upload normalized object
- update job status
- publish `audio.normalized`

Transcription worker:

- consume `audio.normalized`
- download normalized object
- run Whisper transcription
- store transcript in DB
- update job status
- publish `audio.transcribed`

Embedding worker (future):

- consume `audio.transcribed`
- generate embeddings and store vector data
- publish `audio.embedded`

---

## 4) Reliability Rules

- workers use manual acknowledgements
- ACK only after durable success (DB + storage + next publish)
- transient failures retry with bounded backoff
- exhausted failures route to DLQ
- handlers are idempotent for redelivered messages

---

## 5) Data Contracts

Use DTO/event payloads to avoid tight coupling between storage entities and API/worker contracts.

Event envelope should include:

- `eventType`
- `eventVersion`
- `eventId`
- `occurredAt`
- `traceId`
- `payload`

Payload baseline fields:

- `audioFileId`
- `jobId`
- `objectKey`

---

## 6) Data Storage Layout

Object storage:

- raw audio object path
- normalized audio object path

PostgreSQL:

- `audiofile` for upload metadata
- `audio_processing_jobs` for stage/status lifecycle
- `transcripts` for generated transcript data

---

## 7) API Read Endpoints (Target)

- `GET /api/jobs/{jobId}` for stage/status/retry/error
- `GET /api/audio/{audioId}/transcript` for transcript retrieval

Optional aggregate endpoint:

- `GET /api/audio/{audioId}` to return metadata + latest processing state

---

## 8) Local Development Baseline

Use Docker for local infra:

- RabbitMQ
- PostgreSQL
- MinIO

Run API and workers separately for clear service boundaries.

---

## 9) Why This Design

This design keeps upload latency low, allows horizontal worker scaling, and isolates failures by stage.

It is designed to model real distributed backend behavior under load and failure, not only happy-path functionality.
