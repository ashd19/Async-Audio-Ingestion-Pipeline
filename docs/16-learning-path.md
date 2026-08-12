# Detailed Learning Path

Use this document as the study order for the codebase. It is designed for someone who already understands the high-level goal, but needs a detailed, code-anchored path through the implementation.

## Goal

By the end of this path, you should understand:

- how the upload request flows through the Java API
- how storage, database state, and async events connect
- how the Python workers continue the pipeline
- where the code and documentation disagree
- what the main architectural risks and likely bugs are

## The System In One Sentence

This repository implements an async audio ingestion pipeline:

1. a client uploads audio through a Spring Boot API
2. the API stores the raw file in MinIO
3. the API creates a job record in the database
4. the API publishes an event to RabbitMQ
5. Python workers consume events to normalize and transcribe audio
6. the API exposes job status and transcript retrieval endpoints

## Recommended Study Order

### 1. Read the system story first

Start here:

- [README.md](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/README.md)
- [docs/01-overview.md](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/docs/01-overview.md)
- [docs/02-request-flow.md](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/docs/02-request-flow.md)

What to extract:

- what problem the repo is trying to solve
- the intended end-to-end architecture
- the expected request and worker lifecycle

Checkpoint:

- You should be able to describe the full upload-to-transcript flow without looking at the code.

### 2. Understand the HTTP API surface

Read:

- [AudioPipeline/src/main/java/com/AudioPipeline/controller/FileController.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/controller/FileController.java:20)
- [docs/04-api-reference.md](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/docs/04-api-reference.md)

What matters:

- `POST /api/upload` accepts the file and delegates to the service
- `GET /api/jobs/{jobId}` reads job state
- `GET /api/audio/{audioId}/transcript` reads transcript state
- `GET /api/files/{objectKey}` streams an object back from MinIO

Important code notes:

- `FileController` is intentionally thin
- the controller’s main job is request/response wiring
- almost all real business logic lives in `AudioFileService`

Checkpoint:

- You should know which endpoint starts processing and which endpoints are only read-side APIs.

### 3. Learn the synchronous core: `AudioFileService`

Read carefully:

- [AudioPipeline/src/main/java/com/AudioPipeline/service/AudioFileService.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/service/AudioFileService.java:29)
- [docs/12-code-walkthrough.md](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/docs/12-code-walkthrough.md)

This is the most important Java class in the repo.

Focus on `uploadFile()`:

1. validate file presence
2. ensure MinIO bucket exists
3. build object name
4. upload the file to MinIO
5. save `AudioFilesEntity`
6. create `AudioProcessingJobEntity`
7. publish the first event
8. return `AudioFileDto`

Why this class matters:

- it connects controllers, repositories, MinIO, DTOs, and async publishing
- it is the boundary between request-time work and background work

Checkpoint:

- You should be able to explain why `AudioFileService` is the main bridge node in the graph.

### 4. Understand the event contract

Read:

- [AudioPipeline/src/main/java/com/AudioPipeline/service/AudioEventPublisher.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/service/AudioEventPublisher.java:12)
- [AudioPipeline/src/main/java/com/AudioPipeline/dto/AudioPipelineEvent.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/dto/AudioPipelineEvent.java:5)
- [AudioPipeline/src/main/java/com/AudioPipeline/dto/AudioStagePayload.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/dto/AudioStagePayload.java:3)

What to learn:

- `AudioEventPublisher` is the seam from Java business logic into RabbitMQ
- it packages `audioFileId`, `jobId`, and `objectKey` into the payload
- it sends the message using exchange and routing key values from config

Why the graph highlights it:

- it connects pipeline state, event payload schema, and queue topology

Checkpoint:

- You should be able to describe the exact message shape published after upload.

### 5. Understand RabbitMQ topology and configuration

Read:

- [AudioPipeline/src/main/java/com/AudioPipeline/Configuration/RabbitMqProperties.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/Configuration/RabbitMqProperties.java:5)
- [AudioPipeline/src/main/java/com/AudioPipeline/Configuration/RabbitMqConfig.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/Configuration/RabbitMqConfig.java:18)
- [AudioPipeline/src/main/resources/application.yaml](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/resources/application.yaml:22)

This is the configuration backbone of the async system.

Understand:

- topic exchange for main pipeline flow
- direct exchange for DLQs
- normalization, transcription, and embedding queues
- routing keys for uploaded, normalized, and transcribed events
- how Spring binds queues and routing keys together

Why `RabbitMqProperties` is a bridge node:

- `AudioEventPublisher` reads it to publish
- `RabbitMqConfig` reads it to define topology
- the Python workers reconstruct equivalent topology using environment variables

Checkpoint:

- You should know how `audio.uploaded` gets from the Java app into the normalization queue.

### 6. Understand persistence and job tracking

Read:

- [docs/05-database.md](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/docs/05-database.md)
- the entity and repository classes under `AudioPipeline/src/main/java/com/AudioPipeline/entity/`
- the repository classes under `AudioPipeline/src/main/java/com/AudioPipeline/repository/`

Focus especially on:

- `AudioFilesEntity`
- `AudioProcessingJobEntity`
- `TranscriptEntity`

What matters:

- `AudioFilesEntity` tracks uploaded files
- `AudioProcessingJobEntity` tracks pipeline state
- `TranscriptEntity` stores transcript output

Checkpoint:

- You should know which table is the source of truth for pipeline progress.

### 7. Understand the worker runtime

Read:

- [workers/worker_base.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/worker_base.py:19)
- [workers/README.md](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/README.md)

This file is the Python foundation layer.

Key responsibilities:

- connect to RabbitMQ
- connect to MinIO
- connect to PostgreSQL
- declare exchanges, queues, and bindings
- update job state
- publish next-stage events
- provide idempotency checks
- manage ACK/NACK behavior

Study these methods in detail:

- `_connect_services()`
- `update_job_status()`
- `publish_event()`
- `check_idempotency()`
- `handle_message()`
- `start()`

Checkpoint:

- You should understand how a worker consumes a message and decides to ACK or requeue.

### 8. Study the normalization worker

Read:

- [workers/normalization_worker.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/normalization_worker.py:8)

Understand this flow:

1. parse event payload
2. run idempotency check
3. mark job `PROCESSING`
4. download source audio from MinIO
5. normalize with FFmpeg
6. upload normalized file to MinIO
7. mark job `COMPLETED`
8. publish next event with normalized object key

Checkpoint:

- You should be able to state what artifact is produced by normalization and where it is stored.

### 9. Study the transcription worker

Read:

- [workers/transcription_worker.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/transcription_worker.py:9)

Understand this flow:

1. parse normalized-audio event
2. run idempotency check
3. mark job `PROCESSING`
4. download normalized audio
5. transcribe with Whisper
6. save transcript to database
7. update job to `COMPLETED`
8. publish transcribed event

Checkpoint:

- You should know that the transcript text is stored in the database, not just in object storage.

### 10. Understand the runtime environment

Read:

- [AudioPipeline/src/main/resources/application.yaml](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/resources/application.yaml:1)
- [docker-compose.yml](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/docker-compose.yml:3)
- [docs/09-config.md](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/docs/09-config.md)
- [docs/10-deployment.md](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/docs/10-deployment.md)

What to verify:

- what is actually provisioned locally
- which values come from env vars
- which defaults are embedded in code
- where the Java app and Python workers must agree

Important observation:

- the app defaults to PostgreSQL
- `docker-compose.yml` only provisions RabbitMQ and MinIO
- PostgreSQL is not provisioned here

Checkpoint:

- You should be able to explain what extra dependency is required to actually run the whole pipeline locally.

### 11. Finish with the gaps, risks, and contradictions

Read:

- [docs/11-security.md](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/docs/11-security.md)
- [docs/14-questions.md](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/docs/14-questions.md)
- [docs/15-improvements.md](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/docs/15-improvements.md)

This is where you separate “what the repo says” from “what the code actually does.”

Checkpoint:

- You should have a list of issues you would verify before trusting this in production.

## Answers To The Graph’s Suggested Questions

### Why does `AudioEventPublisher` connect `Pipeline Event Payloads` to `RabbitMQ Topology`, `Transcript DTOs And Files`, and `MinIO Configuration`?

Because it is the handoff point between the synchronous API and the async pipeline.

What it actually does:

- receives `audioFileId`, `jobId`, `objectKey`, and `traceId`
- creates an `AudioPipelineEvent`
- embeds those values in `AudioStagePayload`
- publishes the message to the configured exchange using the configured routing key

Code anchors:

- [AudioEventPublisher.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/service/AudioEventPublisher.java:25)
- [AudioPipelineEvent.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/dto/AudioPipelineEvent.java:5)
- [AudioStagePayload.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/dto/AudioStagePayload.java:3)
- [AudioFileService.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/service/AudioFileService.java:88)

Why the graph sees it as a bridge:

- upstream, it depends on upload-time state created by `AudioFileService`
- sideways, it depends on the RabbitMQ config contract
- downstream, workers consume the payload it creates

### Why does `RabbitMqProperties` connect `RabbitMQ Topology` to `Pipeline Event Payloads`?

Because it is the shared config object that defines the naming contract of the async system.

It provides:

- exchange name
- queue names
- DLQ names
- routing keys

Those values are used in two places:

1. `RabbitMqConfig` creates the exchanges, queues, and bindings
2. `AudioEventPublisher` publishes messages to that same exchange and routing key set

Code anchors:

- [RabbitMqProperties.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/Configuration/RabbitMqProperties.java:5)
- [RabbitMqConfig.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/Configuration/RabbitMqConfig.java:22)
- [AudioEventPublisher.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/service/AudioEventPublisher.java:39)

Why this matters:

- if these values drift, publishers and consumers stop agreeing on where events should go

### Why does `AudioFileService` connect `Transcript DTOs And Files` to `File API Controller`, `Transcript Persistence`, `MinIO Configuration`, and `Pipeline Event Payloads`?

Because `AudioFileService` owns the main request-time orchestration.

Inside `uploadFile()` it:

1. validates input
2. ensures bucket existence
3. uploads the file to MinIO
4. saves file metadata
5. creates the job record
6. publishes the uploaded event
7. returns the DTO used by the controller

It also owns:

- file download path
- job status lookup
- transcript lookup

Code anchors:

- [AudioFileService.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/service/AudioFileService.java:53)
- [AudioFileService.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/service/AudioFileService.java:118)
- [AudioFileService.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/service/AudioFileService.java:145)
- [AudioFileService.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/service/AudioFileService.java:166)
- [FileController.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/controller/FileController.java:29)

## The Most Important Things You Need To Know

### 1. This repo is really two systems joined by a queue

There is:

- a Java request/response system
- a Python background processing system

The contract between them is the RabbitMQ event shape plus shared configuration values.

### 2. `AudioFileService` is the main class to master first

If you understand `AudioFileService`, you understand:

- how uploads start
- how state enters the database
- how async work is triggered
- what data the workers receive

### 3. The workers duplicate some topology setup themselves

The Java app defines RabbitMQ topology in Spring config.

The Python workers also declare exchanges, queues, DLQs, and bindings in `WorkerBase`.

That means:

- topology must stay consistent across languages
- drift is possible
- configuration mismatches are a real risk

Relevant code:

- [RabbitMqConfig.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/Configuration/RabbitMqConfig.java:22)
- [worker_base.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/worker_base.py:77)

### 4. The pipeline appears to have a real stage/idempotency bug

This is the highest-priority behavior issue to understand.

In Java upload flow:

- the job is created with stage `UPLOAD`
- the status is set to `QUEUED`

Code:

- [AudioFileService.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/service/AudioFileService.java:79)

In the normalization worker:

- it calls `check_idempotency(job_id, "NORMALIZATION", trace_id)`

Code:

- [normalization_worker.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/normalization_worker.py:59)

The guard logic allows processing only when:

- previous stage completed and current stage differs, or
- current stage already equals expected stage and status is `QUEUED` or `FAILED`

Code:

- [worker_base.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/worker_base.py:330)

For a freshly created job at `UPLOAD/QUEUED`, neither condition matches `NORMALIZATION`.

So the worker likely:

- treats the event as duplicate
- returns `True`
- ACKs the message
- never performs normalization

This is likely the most important implementation bug in the repository.

### 5. Event semantics are inconsistent

Java publishes:

- event type `audio.uploaded`

Python publishes:

- event type `AUDIO_NORMALIZED`
- event type `AUDIO_TRANSCRIBED`

But Python uses routing keys:

- `audio.normalized`
- `audio.transcribed`

Code anchors:

- [AudioEventPublisher.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/service/AudioEventPublisher.java:26)
- [normalization_worker.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/normalization_worker.py:119)
- [transcription_worker.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/transcription_worker.py:162)

This may not break routing directly, but it weakens the event contract and makes observability harder.

### 6. Local infrastructure is incomplete

`application.yaml` assumes PostgreSQL by default:

- [application.yaml](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/resources/application.yaml:4)

But `docker-compose.yml` provisions only:

- RabbitMQ
- MinIO

Code anchor:

- [docker-compose.yml](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/docker-compose.yml:3)

So the full local stack requires an additional PostgreSQL setup that is not included here.

## How To Tackle This Practically

Use this checklist while studying:

- Read the docs first, then verify each claim in code.
- Trace `POST /api/upload` line by line.
- Write down the exact DB state after upload.
- Write down the exact event payload after upload.
- Trace what the normalization worker expects that payload and job state to look like.
- Verify whether the stage/idempotency logic really blocks the first worker.
- Compare Java RabbitMQ names with Python env var defaults.
- Compare the intended architecture docs with what actually exists in `docker-compose.yml`.

## If You Only Have Time For Five Files

Read these first:

1. [AudioFileService.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/service/AudioFileService.java:29)
2. [AudioEventPublisher.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/service/AudioEventPublisher.java:12)
3. [RabbitMqConfig.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/Configuration/RabbitMqConfig.java:18)
4. [worker_base.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/worker_base.py:19)
5. [normalization_worker.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/normalization_worker.py:8)

## Next Best Follow-Up

After reading this path, the best next exercise is:

1. trace one upload from controller to published event
2. trace one normalization message from consume to ACK/NACK
3. confirm the idempotency behavior against the code

If needed, this document can be followed by a second guide focused only on:

- database schema and state transitions
- cross-language contract drift
- production-readiness gaps
