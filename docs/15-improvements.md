# Improvements And Technical Debt

This section is intentionally pragmatic: the highest-value changes are the ones that reduce operational risk and cross-language drift.

## 1. Remove committed secrets and rotate credentials

Why first:

- current repo state is unsafe regardless of code quality

Work:

- purge credentials from tracked env files
- rotate exposed DB credentials
- replace with templates and secret injection

## 2. Add proper authentication and authorization

Why:

- every API endpoint is currently public

Work:

- add Spring Security
- define machine/user access model
- add ownership checks for transcript/file retrieval

## 3. Add explicit database migrations

Why:

- `ddl-auto=update` is fragile for long-lived systems
- Java and Python both depend on shared schema

Work:

- introduce Flyway or Liquibase
- codify tables, indexes, and constraints
- remove schema mutation from app startup

## 4. Fix cross-language contract drift

Why:

- Java and Python duplicate queue names, routing keys, event contracts, and status semantics

Work:

- define a shared contract file or generated constants
- standardize `eventType` values
- standardize stage/status enums

## 5. Fix worker idempotency and retry behavior

Why:

- current logic appears to skip initial normalization
- `MAX_RETRIES` and `retry_count` are mostly cosmetic today

Work:

- make stage transitions explicit
- increment retry counts
- classify transient vs permanent errors
- implement bounded retries with backoff
- route poison messages deterministically

## 6. Add real health endpoints

Why:

- current startup probes use a business endpoint

Work:

- add `/actuator/health` or equivalent
- optionally expose worker health/readiness endpoints

## 7. Provision PostgreSQL in local infrastructure

Why:

- current local story depends on external DB setup or committed remote credentials

Work:

- add PostgreSQL service to Compose
- give Java and workers a default local DB path

## 8. Separate source from generated/runtime artifacts

Why:

- logs, PIDs, `target/`, venvs, `.DS_Store`, and local traces make the repo noisy

Work:

- tighten `.gitignore`
- remove generated artifacts from source control
- keep only intentional sample assets

## 9. Improve API error handling

Why:

- only `IllegalArgumentException` (`400`) and `JobNotFoundException` (`404`) are mapped; other errors (MinIO, DB, RabbitMQ) still surface as 500 without a stable error body

Work:

- add `@ControllerAdvice`
- map not-found to `404`
- define stable error schema

## 10. Add tests around the actual business flow

Why:

- current test suite only checks Spring context startup

Work:

- controller tests for each endpoint
- service tests for upload and download edge cases
- worker unit tests for idempotency and failure handling
- integration tests with RabbitMQ/MinIO/PostgreSQL via Testcontainers or Docker Compose

## 11. Revisit `GET /api/files/{objectKey}`

Why:

- path-variable approach is awkward for slash-containing keys

Work:

- use query parameter or encoded storage identifier
- add auth before exposing object download

## 12. Add constraints and indexes

Why:

- integrity and query performance are under-specified

Work:

- foreign keys where appropriate
- index transcript lookup paths
- consider uniqueness rules for transcripts per audio/job

## 13. Decide whether embedding is real roadmap or dead config

Why:

- queue topology and docs mention embedding, code does not implement it

Work:

- either implement the stage
- or remove dormant topology and docs until needed

## 14. Unify deployment model

Why:

- `docker-compose.yml`, `start-services.sh`, and `start-everything.sh` overlap and diverge

Work:

- keep one supported local startup path
- containerize API/workers if that is the intended operational model

## 15. Add observability beyond logs

Why:

- async systems are hard to maintain without metrics and health visibility

Work:

- structured Java logging with trace IDs
- queue depth metrics
- worker success/failure counters
- processing latency metrics
- dashboarding
