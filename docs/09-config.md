# Configuration

This document explains environment variables and configuration files used by the repository.

Related docs:

- [Deployment](./10-deployment.md)
- [Security](./11-security.md)

## Configuration Sources

### Java API

Primary file:

- [`AudioPipeline/src/main/resources/application.yaml`](../AudioPipeline/src/main/resources/application.yaml)

Imported env files:

- local `.env`
- parent `../.env`

via:

```yaml
spring.config.import: optional:file:.env[.properties],optional:file:../.env[.properties]
```

### Python workers

Primary files:

- [`workers/.env`](../workers/.env)
- [`workers/.env.example`](../workers/.env.example)

Loaded by:

- `load_dotenv()` in [`workers/worker_base.py`](../workers/worker_base.py)

## Java Configuration Variables

### Datasource

- `SPRING_DATASOURCE_URL`
  - JDBC URL for PostgreSQL
- `SPRING_DATASOURCE_USERNAME`
  - database username
- `SPRING_DATASOURCE_PASSWORD`
  - database password
- `SPRING_DATASOURCE_DRIVER`
  - defaults to `org.postgresql.Driver`

### JPA / Hibernate

- `SPRING_JPA_HIBERNATE_DDL_AUTO`
  - defaults to `update`
  - controls schema mutation behavior
- `SPRING_JPA_SHOW_SQL`
  - defaults to `true`
  - enables SQL logging
- `SPRING_JPA_DATABASE_PLATFORM`
  - defaults to `org.hibernate.dialect.PostgreSQLDialect`

### Multipart limits

- `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE`
  - defaults to `20MB`
- `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE`
  - defaults to `20MB`

### RabbitMQ connection

- `SPRING_RABBITMQ_HOST`
  - defaults to `localhost`
- `SPRING_RABBITMQ_PORT`
  - defaults to `5672`
- `SPRING_RABBITMQ_USERNAME`
  - defaults to `guest`
- `SPRING_RABBITMQ_PASSWORD`
  - defaults to `guest`

### App-specific RabbitMQ topology

- `APP_RABBITMQ_EXCHANGE`
- `APP_RABBITMQ_NORMALIZATION_QUEUE`
- `APP_RABBITMQ_TRANSCRIPTION_QUEUE`
- `APP_RABBITMQ_EMBEDDING_QUEUE`
- `APP_RABBITMQ_NORMALIZATION_DLQ`
- `APP_RABBITMQ_TRANSCRIPTION_DLQ`
- `APP_RABBITMQ_EMBEDDING_DLQ`
- `APP_RABBITMQ_DEAD_LETTER_EXCHANGE`
- `APP_RABBITMQ_UPLOADED_ROUTING_KEY`
- `APP_RABBITMQ_NORMALIZED_ROUTING_KEY`
- `APP_RABBITMQ_TRANSCRIBED_ROUTING_KEY`
- `APP_RABBITMQ_EMBEDDED_ROUTING_KEY`

These are bound into [`RabbitMqProperties`](../AudioPipeline/src/main/java/com/AudioPipeline/Configuration/RabbitMqProperties.java).

### MinIO

- `MINIO_URL`
  - defaults to `http://localhost:9000`
- `MINIO_ACCESS_KEY`
  - defaults to `minioadmin`
- `MINIO_SECRET_KEY`
  - defaults to `minioadmin`
- `MINIO_BUCKET`
  - defaults to `audio-files`

## Worker Configuration Variables

### RabbitMQ

- `RABBITMQ_HOST`
- `RABBITMQ_PORT`
- `RABBITMQ_USER`
- `RABBITMQ_PASSWORD`
- `RABBITMQ_VHOST`
- `RABBITMQ_EXCHANGE`
- `NORMALIZATION_QUEUE`
- `TRANSCRIPTION_QUEUE`
- `EMBEDDING_QUEUE` (used in base defaults/topology, not documented in `.env.example`)
- `NORMALIZATION_DLQ`
- `TRANSCRIPTION_DLQ`
- `EMBEDDING_DLQ`
- `DEAD_LETTER_EXCHANGE`
- `UPLOADED_ROUTING_KEY`
- `NORMALIZED_ROUTING_KEY`
- `TRANSCRIBED_ROUTING_KEY`

Important note:

- not all of these are present in `.env.example`, but `worker_base.py` still reads defaults for them

### MinIO

- `MINIO_ENDPOINT`
- `MINIO_ACCESS_KEY`
- `MINIO_SECRET_KEY`
- `MINIO_BUCKET`
- `MINIO_SECURE`

### Database

Workers support two configuration styles:

1. single URL:
   - `DATABASE_URL`
   - or `DB_URL`
2. decomposed fields:
   - `DB_HOST`
   - `DB_PORT`
   - `DB_NAME`
   - `DB_USER`
   - `DB_PASSWORD`
   - `DB_SSLMODE`

### Worker runtime

- `MAX_RETRIES`
- `RETRY_BACKOFF_SECONDS`
- `LOG_LEVEL`
- `WHISPER_MODEL`

Important note:

- `MAX_RETRIES` and `RETRY_BACKOFF_SECONDS` are read, but the current consume loop does not implement a true bounded retry strategy. Messages are simply requeued on generic processing failure.

## Docker Compose Configuration

Defined in [`docker-compose.yml`](../docker-compose.yml).

### RabbitMQ container

- default user/password set to `guest/guest`
- exposes ports `5672` and `15672`
- stores data in `rabbitmq_data`

### MinIO container

- root credentials `minioadmin/minioadmin`
- exposes ports `9000` and `9001`
- stores data in `minio_data`

## Checked-In Secrets

The repository currently contains real-looking database credentials in:

- root `.env`
- `workers/.env`
- `workers/.env.example`

This documentation intentionally does not repeat the secret values. The presence of those values is itself a security issue.

## Configuration Gaps

- no environment-specific profiles beyond Spring defaults
- no secrets manager integration
- no config validation on startup beyond type coercion
- no central shared contract file between Java and Python for queue names/routing keys
