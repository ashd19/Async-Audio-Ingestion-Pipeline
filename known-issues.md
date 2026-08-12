# Known Issues

This is the short version. For the full request trace, see [api-lifecycle.md](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/api-lifecycle.md). For the longer docs set, see [docs/17-issues.md](/Users/ash/coding/projects/Async-Audio-Ingestion-Pipeline/docs/17-issues.md).

## Critical

- RabbitMQ publish is not transactionally tied to the database commit.
  The API saves records and publishes `audio.uploaded` before the Spring transaction fully commits, so a worker can receive an event for a job row that is not yet committed or never commits at all.

- Worker retry behavior is effectively unbounded.
  Failures are `NACK`ed with `requeue=True`, but `retry_count`, `MAX_RETRIES`, and `RETRY_BACKOFF_SECONDS` are not actually enforced.

- Redelivered messages can be ACKed while the job is stuck in `PROCESSING`.
  The idempotency logic only accepts a narrow set of states. After some partial failures, a retried message is treated as a duplicate and acknowledged instead of being finished or failed cleanly.

- The final transcription `object_key` is misleading.
  The job is marked with `transcripts/<audioId>.txt`, but the transcription worker does not upload any transcript file to MinIO. The transcript is stored only in PostgreSQL.

## Major

- The request is only partially asynchronous.
  `POST /api/upload` still performs bucket checks, raw MinIO upload, and database inserts before returning `202 Accepted`.

- The embedding stage is configured but not implemented.
  `audio.transcribed` is published to `audio.embedding.queue`, but there is no embedding worker in the repository.

- RabbitMQ topology is duplicated in Java and Python.
  Exchange, queue, DLQ, and routing-key setup exists in both codebases, which creates contract drift risk.

- Local infrastructure/setup is inconsistent.
  The runtime expects PostgreSQL, but the repository’s setup and documentation have mixed signals across Compose, scripts, and older artifacts.

## Operational

- MinIO writes are outside the database transaction.
  If object upload succeeds and the DB work fails, orphaned objects can remain in storage.

- `started_at` is overwritten per stage.
  It behaves like “current stage started at”, not “job first started at”.

- Confidence scoring is approximate.
  The stored transcript confidence is derived from Whisper `no_speech_prob`, which is not the same as transcript accuracy confidence.

## Suggested Fix Order

1. Make event publishing happen only after successful DB commit, or use an outbox pattern.
2. Fix worker retry semantics: increment `retry_count`, classify permanent vs transient failures, and bound retries.
3. Fix idempotency/state handling for redeliveries while a job is in `PROCESSING`.
4. Decide whether transcript artifacts belong in MinIO or only in PostgreSQL, then make `object_key` reflect reality.
5. Either implement the embedding consumer or remove that stage from the active topology.
