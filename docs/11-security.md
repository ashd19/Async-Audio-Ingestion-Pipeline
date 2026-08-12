# Security

This document focuses on implemented security controls, missing controls, and concrete risks visible in the repository.

Related docs:

- [Authentication](./06-authentication.md)
- [Config](./09-config.md)
- [Questions](./14-questions.md)

## Authentication

There is no authentication.

Impact:

- any reachable client can use the API
- any transcript or file is fetchable without identity checks

## Authorization

There is no authorization.

Impact:

- no ownership checks
- no role-based access
- no environment separation at the app layer

## Secrets

### Current state

Committed env files contain real-looking credentials, including database connection strings and passwords.

Files involved:

- [`/.env`](../.env)
- [`workers/.env`](../workers/.env)
- [`workers/.env.example`](../workers/.env.example)

### Risk

- credential leakage through source control
- accidental re-use in multiple environments
- onboarding engineers may unknowingly use shared credentials

### Documentation choice

This doc intentionally names the files but does not reproduce the secret values.

## Validation

Implemented:

- upload file presence / non-empty check
- multipart size caps through Spring servlet config
- blank object key rejection in download service

Missing:

- content-type allowlist
- extension allowlist
- max filename length enforcement before storage key generation
- audio format verification
- malware scanning
- transcript output sanitization rules

## Transport Security

Not configured in repo:

- HTTPS termination
- TLS between services
- secure cookies or session config

Local default endpoints are plain HTTP.

## Rate Limiting

Not implemented.

Impact:

- upload endpoint can be abused for storage/broker load
- no brute-force or flood protection

## Message Security

RabbitMQ queues are durable and DLQs exist, which helps reliability, not access control.

Missing:

- message signing
- payload encryption
- broker permission partitioning documented in repo

## Database Security

Positive:

- uses parameterized SQL in workers
- uses JPA repository abstractions in Java

Risks:

- no FK constraints
- no least-privilege DB role documented
- schema auto-update enabled in application runtime

## File Access Security

The API exposes `GET /api/files/{objectKey}`.

Risks:

- no auth
- no ownership checks
- path-variable routing may mis-handle nested object keys
- direct access to internal storage keys leaks storage layout

## Logging

Positive:

- workers use structured JSON logs with `traceId`

Risks:

- logs may still expose operational details and filenames
- Java logs do not consistently attach trace ID
- committed runtime logs expose environment details

## Third-Party / Supply Chain Observations

- Python virtualenvs are present in the repo tree, which is noisy and may accidentally carry compiled artifacts or vulnerable packages into source control history.
- `minio/minio:latest` in Compose is not pinned to a digest or fixed version.

## Concrete Security Concerns In This Repo

1. Committed secrets in env files.
2. No auth on any endpoint.
3. No rate limiting.
4. `ddl-auto=update` against PostgreSQL.
5. Runtime/build artifacts committed or retained in repo tree.
6. Local helper scripts background processes without supervision or secret hygiene.

## Existing Positive Controls

To avoid overstating risk, these controls do exist:

- parameterized SQL in workers
- durable queues and DLQs
- multipart size limits
- object key sanitization for filenames
- no direct shell execution from request paths

They are useful, but not enough to treat this as a secure production service.
