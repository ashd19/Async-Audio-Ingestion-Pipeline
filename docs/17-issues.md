# Known Issues

This document lists the main issues currently visible in the repository from code and documentation review. The goal is to give you a practical triage list, not a generic architecture summary.

## High Severity

### 1. Normalization worker likely skips fresh jobs due to stage/idempotency mismatch

Why this matters:

- this can break the main async pipeline at the first worker stage
- an upload may be accepted and queued, but never actually normalized

What the Java API does:

- creates a new job with stage `UPLOAD`
- sets the status to `QUEUED`

Code:

- [AudioFileService.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/service/AudioFileService.java:79)

What the normalization worker expects:

- it calls `check_idempotency(job_id, "NORMALIZATION", trace_id)`

Code:

- [normalization_worker.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/normalization_worker.py:59)

Why this is likely broken:

- `check_idempotency()` allows processing when the previous stage is completed and different, or when the current stage already equals the expected stage and status is `QUEUED` or `FAILED`
- a new job starts at `UPLOAD/QUEUED`
- that does not match `NORMALIZATION`
- the worker likely logs it as duplicate and returns success
- returning success causes the message to be ACKed

Code:

- [worker_base.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/worker_base.py:313)
- [worker_base.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/worker_base.py:367)

Likely effect:

- upload succeeds
- event is published
- normalization worker consumes message
- message is ACKed without processing
- pipeline stalls

### 2. Local infrastructure is incomplete for the default application configuration

Why this matters:

- the repo does not provision everything needed for the default runtime path

What the app expects:

- PostgreSQL by default

Code:

- [application.yaml](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/resources/application.yaml:4)

What the local Docker stack provides:

- RabbitMQ
- MinIO
- no PostgreSQL service

Code:

- [docker-compose.yml](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/docker-compose.yml:3)

Likely effect:

- the API and workers can fail locally unless PostgreSQL is provided separately
- the repo’s local setup story is incomplete

### 3. Cross-language topology contract can drift because Java and Python both define RabbitMQ infrastructure

Why this matters:

- both sides declare exchange, queue, DLQ, and binding behavior
- changes in one implementation can silently break the other

Java topology:

- [RabbitMqConfig.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/Configuration/RabbitMqConfig.java:22)

Python topology:

- [worker_base.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/worker_base.py:77)

Risk:

- queue names or routing keys can diverge
- DLQ behavior can diverge
- a developer may update one side and forget the other

## Medium Severity

### 4. Event naming semantics are inconsistent across stages

Why this matters:

- it weakens the event contract
- it makes logs, debugging, and downstream consumers harder to reason about

Java upload event:

- event type is `audio.uploaded`

Code:

- [AudioEventPublisher.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/service/AudioEventPublisher.java:26)

Python worker events:

- normalization publishes `AUDIO_NORMALIZED`
- transcription publishes `AUDIO_TRANSCRIBED`

Code:

- [normalization_worker.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/normalization_worker.py:120)
- [transcription_worker.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/transcription_worker.py:163)

At the same time, routing keys remain:

- `audio.normalized`
- `audio.transcribed`

Risk:

- transport may still work
- semantic consistency does not

### 5. Retry behavior is simplistic and may create noisy requeue loops

Why this matters:

- transient and non-transient failures are not separated carefully
- repeated requeueing can create churn

Current behavior:

- `process_message()` returns `False` for many failures
- `handle_message()` NACKs with `requeue=True` on general processing failure

Code:

- [worker_base.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/worker_base.py:365)
- [worker_base.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/worker_base.py:371)

Risk:

- poison messages can keep cycling
- permanent failures are not always isolated quickly
- retry counters are not clearly enforced here

### 6. Job status transitions are under-modeled

Why this matters:

- the pipeline depends on `stage` and `status` as the cross-component state machine
- the allowed transitions are not represented as an explicit workflow model

Examples:

- status and stage updates are performed by SQL strings in `WorkerBase`
- stage logic is implicit in worker code and idempotency conditions

Code:

- [worker_base.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/worker_base.py:222)
- [worker_base.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/worker_base.py:313)

Risk:

- invalid transitions are easy to introduce
- behavior becomes harder to reason about as stages increase

### 7. Embedding stage exists in topology but not as a complete implemented flow

Why this matters:

- the pipeline shape suggests a third processing stage
- the codebase does not appear to provide a full embedding worker implementation

Visible signs:

- Java defines embedding queue, DLQ, and routing key
- Python base worker also declares embedding queue and binding

Code:

- [RabbitMqProperties.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/Configuration/RabbitMqProperties.java:10)
- [RabbitMqConfig.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/Configuration/RabbitMqConfig.java:60)
- [worker_base.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/worker_base.py:83)

Risk:

- docs and infra imply a fuller pipeline than the implementation actually provides

## Lower Severity But Important

### 8. Bucket creation in request path adds operational coupling to uploads

Why this matters:

- storage provisioning is happening during request processing

Code:

- [AudioFileService.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/service/AudioFileService.java:59)
- [AudioFileService.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/service/AudioFileService.java:106)

Risk:

- first-request latency may be higher
- storage readiness is being mixed into normal application flow

### 9. `GET /api/files/{objectKey}` may be awkward for nested object keys

Why this matters:

- object keys can contain slashes
- the controller maps `objectKey` as a simple path variable

Code:

- [FileController.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/controller/FileController.java:43)

Risk:

- nested object keys may not behave as intended depending on routing setup

### 10. Worker confidence calculation is weakly defined

Why this matters:

- transcript confidence is derived from Whisper segment `no_speech_prob`
- that is not the same as model confidence in transcript correctness

Code:

- [transcription_worker.py](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/workers/transcription_worker.py:57)

Risk:

- stored confidence may be misleading
- consumers may over-trust the field

### 11. The Java service mixes multiple responsibilities

Why this matters:

- `AudioFileService` handles storage, persistence, job creation, event publishing, and read-side operations

Code:

- [AudioFileService.java](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/AudioPipeline/src/main/java/com/AudioPipeline/service/AudioFileService.java:29)

Risk:

- future changes become harder to isolate
- tests will tend to be broader than necessary

## Documentation And Operational Issues

### 12. Documentation may overstate what is actually runnable

Why this matters:

- the repo contains strong architecture documentation
- some parts of the actual runnable system do not line up cleanly with that narrative

Examples:

- default DB expectations versus compose stack
- embedding stage presence versus implementation completeness
- async processing story versus stage/idempotency bug

Suggested verification docs:

- [docs/02-request-flow.md](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/docs/02-request-flow.md)
- [docs/09-config.md](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/docs/09-config.md)
- [docs/15-improvements.md](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/docs/15-improvements.md)

### 13. Graph output had integrity warnings and should not be treated as proof

Why this matters:

- the graph was useful for orientation
- it reported dangling and collapsed edges during build diagnostics

Implication:

- graph findings are good for discovery
- code should remain the source of truth

## Recommended Triage Order

1. Confirm the normalization worker idempotency bug against a real upload flow.
2. Decide the intended state machine for `stage` and `status`.
3. Unify RabbitMQ topology ownership or at least centralize the contract.
4. Provision PostgreSQL in the local stack or document the external dependency clearly.
5. Normalize event naming conventions across Java and Python.
6. Decide whether embedding is a real stage or remove it from the active contract.

## Short Version

If you only remember five issues, remember these:

1. The pipeline likely breaks at normalization because of stage/idempotency mismatch.
2. The local stack is incomplete because PostgreSQL is not provisioned.
3. Java and Python duplicate RabbitMQ topology logic, which invites drift.
4. Event naming is inconsistent across stages.
5. The embedding stage looks partially designed but not fully implemented.
