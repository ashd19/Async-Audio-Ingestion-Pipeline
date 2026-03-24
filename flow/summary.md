# RabbitMQ Progress Summary

## Current State

RabbitMQ flow is now wired into the Spring app beyond config/topology.

Implemented flow:

`POST /api/upload` -> publish `audio.uploaded` -> normalization listener -> publish `audio.normalized` -> transcription listener -> publish `audio.transcribed` -> embedding listener -> publish `audio.embedded`

## What Was Added

- Event publisher service (`AudioEventPublisher`) using `RabbitTemplate`
- Event contract DTOs:
  - `AudioPipelineEvent` (envelope)
  - `AudioStagePayload` (payload)
- Processing job persistence:
  - `audio_processing_jobs` entity/repository
- Listener-based stage transitions in `AudioFileService`
- Upload response enriched with `audioId`, `jobId`, `stage`, `status`, `traceId`
- Job status API: `GET /api/jobs/{jobId}`
- Full operational flow doc: `rabbitmq.md`

## Validation Status

- `./AudioPipeline/mvnw -f AudioPipeline/pom.xml -DskipTests compile` passes.
- `test` currently fails due pre-existing test/package mismatch and missing local RabbitMQ in test runtime.

## Remaining Work (from todo reliability goals)

- Retries with bounded backoff
- Idempotency for redeliveries
- Error classification (transient vs permanent)
- DLQ reason metadata and poison-message safeguards
- Transcript table + retrieval endpoint

## Quick Live Demo (Local)

1. Start local dependencies (RabbitMQ + PostgreSQL + MinIO) using your local setup.

2. Start the API:

```bash
./AudioPipeline/mvnw -f AudioPipeline/pom.xml spring-boot:run
```

3. Upload an audio file:

```bash
curl -s -X POST http://localhost:8080/api/upload \
  -F "file=@/absolute/path/to/sample.mp3" \
  -H "Accept: application/json"
```

Expected response fields include: `audioId`, `jobId`, `filePath`, `stage`, `status`, `traceId`.

4. Poll job status (replace `<jobId>` from upload response):

```bash
curl -s http://localhost:8080/api/jobs/<jobId>
```

You should observe progression to `stage=EMBEDDING` and `status=COMPLETED`.

5. (Optional) Check RabbitMQ queues in management UI to confirm message flow and queue drain behavior.
