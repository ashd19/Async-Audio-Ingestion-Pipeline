# API Reference

Related docs:

- [Request Flow](./02-request-flow.md)
- [Authentication](./06-authentication.md)
- [Code Walkthrough](./12-code-walkthrough.md)

Source of truth: [`AudioPipeline/src/main/java/com/AudioPipeline/controller/FileController.java`](../AudioPipeline/src/main/java/com/AudioPipeline/controller/FileController.java)

## Base Path

All endpoints are under `/api`.

## Authentication Requirements

None of the endpoints require authentication.

## Endpoints

### `POST /api/upload`

Uploads a multipart audio file, stores it, creates a job record, publishes an event, and returns queued job metadata.

Source flow:

- controller: [`FileController`](../AudioPipeline/src/main/java/com/AudioPipeline/controller/FileController.java)
- service: [`AudioFileService.uploadFile`](../AudioPipeline/src/main/java/com/AudioPipeline/service/AudioFileService.java)
- publisher: [`AudioEventPublisher`](../AudioPipeline/src/main/java/com/AudioPipeline/service/AudioEventPublisher.java)

#### Method

`POST`

#### Request

Content type:

- `multipart/form-data`

Required form fields:

- `file`: multipart file

Other request inputs:

- client IP is read from `HttpServletRequest.getRemoteAddr()`

#### Validation

- request must include `file`
- file must not be empty
- no explicit size validation in controller/service beyond global multipart limits
- no MIME or extension whitelist

#### Response

Status:

- `202 Accepted`

Body:

```json
{
  "audioId": 1,
  "jobId": 1,
  "filePath": "uuid-safe-filename.mp3",
  "stage": "UPLOAD",
  "status": "QUEUED",
  "traceId": "uuid",
  "statusUrl": "http://localhost:8080/api/jobs/1"
}
```

Exact values vary. Example shape is based on [`AudioFileDto`](../AudioPipeline/src/main/java/com/AudioPipeline/dto/AudioFileDto.java).

#### Internal Flow

1. Controller forwards `MultipartFile` and remote IP to service.
2. Service ensures the MinIO bucket exists.
3. Service writes the raw object to MinIO.
4. Service inserts an `audiofile` row.
5. Service inserts an `audio_processing_jobs` row with `stage=UPLOAD` and `status=QUEUED`.
6. Service publishes an `audio.uploaded` event to RabbitMQ.
7. Controller converts the relative `statusUrl` into an absolute URL.
8. Controller returns `202`.

#### Failure Modes

- empty file -> `400 Bad Request` (body: `File is required`)
- MinIO failures -> exception bubbles up
- PostgreSQL failures -> exception bubbles up
- RabbitMQ publish failures -> exception bubbles up

Only `IllegalArgumentException` (mapped to `400` in `FileController`) and `JobNotFoundException` (`404`) are mapped. Other failures are not normalized into a stable error contract.

### `GET /api/files/{objectKey}`

Streams a stored object back from MinIO.

#### Method

`GET`

#### Request

Path parameter:

- `objectKey`

Important caveat:

- the route uses `{*objectKey}`, so keys containing `/` work (for example `normalized/{audioId}.mp3`). Spring captures the leading `/`; the controller strips it before the MinIO lookup.

#### Validation

- blank key rejected in service (`400 Bad Request`)
- no authorization checks

#### Response

Status:

- `200 OK`

Body:

- raw streamed object bytes as `InputStreamResource`

Headers:

- `Content-Type` from MinIO metadata if present, else inferred from filename
- `Content-Disposition: attachment; filename="..."`
- `Content-Length`

#### Internal Flow

1. Controller calls `downloadFile`.
2. Service executes MinIO `statObject`.
3. Service executes MinIO `getObject`.
4. Controller builds streaming response.

### `GET /api/jobs/{jobId}`

Returns current processing job state.

#### Method

`GET`

#### Request

Path parameter:

- `jobId`

#### Validation

- no explicit path validation beyond type conversion to `Long`

#### Response

Status:

- `200 OK` when found

Body fields from [`AudioJobStatusDto`](../AudioPipeline/src/main/java/com/AudioPipeline/dto/AudioJobStatusDto.java):

- `jobId`
- `audioId`
- `stage`
- `status`
- `retryCount`
- `objectKey`
- `traceId`
- `errorCode`
- `errorMessage`
- `createdAt`
- `startedAt`
- `completedAt`
- `updatedAt`

#### Internal Flow

1. Controller delegates to service.
2. Service loads `AudioProcessingJobEntity` by ID via JPA.
3. Service maps entity to DTO.

#### Failure Modes

- missing job throws `JobNotFoundException`, annotated `@ResponseStatus(404)`, so the API returns `404 Not Found`

### `GET /api/audio/{audioId}/transcript`

Returns the stored transcript DTO for an audio file if present.

#### Method

`GET`

#### Request

Path parameter:

- `audioId`

#### Validation

- no explicit path validation beyond type conversion

#### Response

Status:

- `200 OK` when a transcript exists
- `404 Not Found` when it does not

Body fields from [`TranscriptDto`](../AudioPipeline/src/main/java/com/AudioPipeline/dto/TranscriptDto.java):

- `id`
- `audioFileId`
- `jobId`
- `transcriptText`
- `language`
- `confidence`

#### Internal Flow

1. Controller calls `audioFileService.getTranscript(audioId)`.
2. Service queries `TranscriptRepository.findByAudioFileId`.
3. Service maps entity to DTO.
4. Controller returns DTO or `404`.

## Missing Endpoints

The older `flow/` documents mention future or intended capabilities that are not implemented as endpoints, including:

- audio aggregate fetch
- delete/retry endpoints
- health endpoints
- admin/DLQ endpoints

## API Contract Observations

- The API is internal/demo-grade rather than externally productized.
- There is no OpenAPI spec.
- There is no consistent error response schema.
- There is no paging, filtering, or resource listing.
- DTOs expose stage/status strings directly rather than enums.
