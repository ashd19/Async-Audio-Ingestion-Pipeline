# Dependencies

This document explains the important dependencies actually used by the repository and why they are here.

Related docs:

- [Overview](./01-overview.md)
- [Config](./09-config.md)
- [Deployment](./10-deployment.md)

## Java Dependencies

Defined in [`AudioPipeline/pom.xml`](../AudioPipeline/pom.xml).

### `spring-boot-starter-webmvc`

Why it is used:

- REST controller support
- embedded Tomcat
- multipart request handling
- HTTP response serialization

Where it shows up:

- [`FileController`](../AudioPipeline/src/main/java/com/AudioPipeline/controller/FileController.java)

### `spring-boot-starter-data-jpa`

Why it is used:

- entity mapping
- repository abstraction
- transaction management on the Java side
- schema generation via Hibernate

Where it shows up:

- `entity/`
- `repository/`
- `@Transactional` on upload service

### `postgresql`

Why it is used:

- runtime JDBC driver for the actual configured database

Evidence:

- default datasource is PostgreSQL
- runtime logs show PostgreSQL connections

### `spring-boot-starter-validation`

Why it is present:

- Bean Validation support

How much it is used:

- lightly
- only DTO field annotations are visible, and those are on a response DTO rather than an inbound request DTO

### `spring-boot-starter-amqp`

Why it is used:

- RabbitMQ connection factory
- queue/exchange/binding bean model
- `RabbitTemplate` publisher
- JSON message conversion

Where it shows up:

- [`RabbitMqConfig`](../AudioPipeline/src/main/java/com/AudioPipeline/Configuration/RabbitMqConfig.java)
- [`AudioEventPublisher`](../AudioPipeline/src/main/java/com/AudioPipeline/service/AudioEventPublisher.java)

### `jackson-datatype-jsr310`

Why it is used:

- serializing/deserializing Java time types in RabbitMQ messages and API responses

### `io.minio:minio`

Why it is used:

- MinIO / S3-compatible object storage client for upload and download

Where it shows up:

- [`MinioConfig`](../AudioPipeline/src/main/java/com/AudioPipeline/Configuration/MinioConfig.java)
- [`AudioFileService`](../AudioPipeline/src/main/java/com/AudioPipeline/service/AudioFileService.java)

### `com.squareup.okhttp3:okhttp`

Why it is present:

- transitive or direct HTTP support for MinIO client interactions

Current direct usage in source:

- none

It may not need to be declared explicitly unless required by the chosen MinIO SDK integration.

### `lombok`

Why it is used:

- reduces boilerplate on entities and DTOs

Where it shows up:

- `@Data`
- `@Builder`
- constructor annotations

Tradeoff:

- faster coding, but obscures generated methods unless the reader is comfortable with Lombok

### Test starters

- `spring-boot-starter-data-jpa-test`
- `spring-boot-starter-webmvc-test`

Why they are present:

- test support

Current effective use:

- unit tests use JUnit 5, Mockito, AssertJ and MockMvc from the test starters; there are no Spring-context or integration tests

## Python Dependencies

Defined in [`workers/requirements.txt`](../workers/requirements.txt).

### `pika`

Why it is used:

- RabbitMQ client for worker consume/publish flow

Where it shows up:

- connection setup
- queue declaration
- `basic_ack` / `basic_nack`

### `minio`

Why it is used:

- object download/upload from Python workers

### `ffmpeg-python`

Why it is used:

- Python wrapper around the FFmpeg CLI graph for audio normalization

Important note:

- FFmpeg itself must still be installed on the host

### `openai-whisper`

Why it is used:

- local transcription model and inference

Important operational note:

- first model load may download large model weights depending on host state

### `psycopg2-binary`

Why it is used:

- direct PostgreSQL access from workers

Why direct SQL instead of calling the API:

- workers update job state and insert transcript rows without round-tripping through HTTP

### `python-dotenv`

Why it is used:

- load worker env vars from `workers/.env`

### `requests`

Why it is present:

- comment says health checks

Current source usage:

- none in worker code reviewed here

### `python-json-logger`

Why it is used:

- structured JSON log output with `traceId`

## Infrastructure Dependencies

### Docker / Docker Compose

Why they are used:

- local RabbitMQ and MinIO provisioning
- reproducible dev environment bootstrap

Defined in:

- [`docker-compose.yml`](../docker-compose.yml)
- shell scripts under [`scripts/`](../scripts)

### RabbitMQ image

- `rabbitmq:3-management`

Why:

- queue broker plus management UI on port `15672`

### MinIO image

- `minio/minio:latest`

Why:

- S3-compatible object storage for local development

## Hidden / Indirect Dependencies

These are operational dependencies that are not Java/Python library imports but are required for the system to work:

- Java 21
- Python 3
- FFmpeg
- PostgreSQL instance
- RabbitMQ instance
- MinIO instance
- JMeter if using the load-test flow

## Dependency Observations

- The Java app and Python workers each define parts of the same system contract independently.
- Some dependencies are present but lightly used (`validation`, `requests`, webmvc-test starter).
- The project relies on infrastructure and ML/media binaries outside the package manifests; onboarding needs to account for that explicitly.
