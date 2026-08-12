# Questions, Confusions, And Inconsistencies

This file lists issues that a maintainer should verify before treating the repo as stable.

## 1. Is the normalization worker currently blocked by its own idempotency logic?

Evidence:

- [`workers/normalization_worker.log`](../workers/normalization_worker.log) shows job `1` at `UPLOAD/QUEUED` being skipped as duplicate.
- `check_idempotency` in [`workers/worker_base.py`](../workers/worker_base.py) does not obviously allow the first transition from `UPLOAD/QUEUED` into `NORMALIZATION`.

Why it matters:

- this may stop the async pipeline at the first worker stage

## 2. Which documentation should be trusted when `flow/` conflicts with source?

Examples:

- `flow/rabbitmq.md` describes listener-based stage processing in the Spring app
- actual code contains no `@RabbitListener` methods
- real async processing is in Python workers

Why it matters:

- onboarding from the old docs alone would be misleading

## 3. Is PostgreSQL the intended default, or are H2 files still part of the supported developer workflow?

Evidence:

- [`application.yaml`](../AudioPipeline/src/main/resources/application.yaml) defaults to PostgreSQL
- `AudioPipeline/data/` contains H2 files
- [`H2ConsoleConfiguration.java`](../AudioPipeline/src/main/java/com/AudioPipeline/Configuration/H2ConsoleConfiguration.java) is fully commented out
- `AudioPipeline/data/audiopipeline.trace.db` contains H2 file-lock stack traces

Why it matters:

- current database story is mixed and confusing

## 4. Why is PostgreSQL not provisioned by `docker-compose.yml`?

The project requires PostgreSQL, but Compose only starts RabbitMQ and MinIO.

Why it matters:

- local onboarding depends on external DB setup or committed remote credentials

## 5. Are the committed credentials intentionally shared or accidentally committed?

Files:

- `.env`
- `workers/.env`
- `workers/.env.example`

Why it matters:

- this is the most immediate security concern in the repo

## 6. Why does `start-everything.sh` launch JMeter automatically?

Source:

- [`scripts/start-everything.sh`](../scripts/start-everything.sh)

Why it matters:

- startup and benchmarking are coupled
- maintainers may expect a pure app startup, not an immediate load test

## 7. Is the API readiness probe in `start-everything.sh` correct?

It polls `GET /api/jobs/1`, which throws `Job not found: 1` on a fresh database.

Why it matters:

- the script treats a business-error endpoint as a health check

## 8. What is the intended contract for `eventType` values?

Observed:

- Java publishes lowercase dotted names like `audio.uploaded`
- Python publishes uppercase names like `AUDIO_NORMALIZED`

Why it matters:

- event consumers or observability tooling may eventually rely on `eventType`

## 9. Is `GET /api/files/{objectKey}` expected to work for nested keys?

Workers generate keys such as:

- `normalized/{audioId}.mp3`
- `transcripts/{audioId}.txt`

But the controller maps `objectKey` as a single path variable.

Why it matters:

- slash-containing object keys typically do not map cleanly without wildcard patterns

## 10. Why are there no foreign keys or uniqueness constraints?

Current schema relies on application convention.

Why it matters:

- data integrity can drift silently

## 11. Is `retry_count` intended to be meaningful?

It exists in the schema and DTO, but worker code does not increment it.

Why it matters:

- job status suggests retry visibility that the implementation does not currently provide

## 12. What is the intended role of the embedding stage?

Evidence:

- queue names and routing keys exist
- docs mention embedding
- no embedding worker exists

Why it matters:

- topology and docs imply a third stage that maintainers cannot actually run

## 13. Why are `target/`, logs, PID files, `.DS_Store`, and virtualenvs present in the repository tree?

Why it matters:

- source/noise separation is poor
- reproducibility and review quality suffer

## 14. Should DTO validation annotations be moved onto request models instead of response models?

Evidence:

- `AudioFileDto` has validation annotations, but it is returned to clients rather than used for request binding

Why it matters:

- current validation annotations do not protect the upload boundary

## 15. Is the current single `@SpringBootTest` enough to protect behavior?

There are no controller, service, worker, or integration tests beyond context load.

Why it matters:

- refactors can easily break the cross-component pipeline without detection
