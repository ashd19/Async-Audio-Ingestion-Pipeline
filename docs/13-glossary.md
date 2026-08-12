# Glossary

## Audio file

A raw uploaded media object stored in MinIO and represented by a row in `audiofile`.

## Audio ID

Primary key of the `audiofile` table. Returned as `audioId` in upload responses.

## Job

A pipeline execution record stored in `audio_processing_jobs`. One upload creates one job in the current implementation.

## Job ID

Primary key of `audio_processing_jobs`. Used for polling `GET /api/jobs/{jobId}`.

## Stage

The broad phase of pipeline execution. Current implemented values seen in code:

- `UPLOAD`
- `NORMALIZATION`
- `TRANSCRIPTION`

An `EMBEDDING` stage is described in config/docs but not implemented by a worker.

## Status

The current state inside a stage. Current values in code:

- `QUEUED`
- `PROCESSING`
- `COMPLETED`
- `FAILED`

## Object key

The MinIO object identifier for the current artifact.

Examples:

- raw upload key: `uuid-file.mp3`
- normalized key: `normalized/{audioId}.mp3`
- transcript pseudo-key: `transcripts/{audioId}.txt`

## Trace ID

A UUID used to correlate one upload across API, database state, worker logs, and events.

## MinIO

S3-compatible object storage used for audio artifacts.

## RabbitMQ

Message broker used to hand work from API to workers and between stages.

## Exchange

RabbitMQ routing component. This repo uses topic exchange `audio.processing.exchange`.

## Queue

RabbitMQ buffer consumed by a stage worker, for example `audio.normalization.queue`.

## DLQ

Dead-letter queue. Holds messages that are rejected without requeue.

## Normalization

Audio loudness and output-format processing done with FFmpeg before transcription.

## Whisper

The open-source speech recognition package used by the transcription worker.

## Transcript

Persisted text result generated from normalized audio and stored in `transcripts`.

## Synchronous baseline

The architecture described in the README where upload and storage/persistence happen inline in the request thread. This baseline is documented, but not implemented as separate live code in the repo.

## Async pipeline

The implemented architecture where upload returns quickly and downstream work is processed through RabbitMQ-backed workers.
