# Folder Guide

This document explains every major folder in the repository and why it exists.

Related docs:

- [Overview](./01-overview.md)
- [Business Logic](./07-business-logic.md)
- [Deployment](./10-deployment.md)

## Repository Root

The root combines product code, runtime helpers, historical notes, and generated artifacts. It is not a clean source-only repository.

## `AudioPipeline/`

Purpose: Maven module containing the Spring Boot application.

Subfolders:

- `src/main/java/com/AudioPipeline/`
  - application entrypoint
  - configuration beans
  - REST controller
  - DTOs
  - JPA entities
  - JPA repositories
  - service layer
- `src/main/resources/`
  - runtime YAML configuration
- `src/test/java/`
  - one Spring context smoke test
- `.mvn/`
  - Maven wrapper metadata
- `data/`
  - checked-in H2 database files from an older/local setup
- `target/`
  - compiled output and Surefire reports from local builds

Why it exists:

- it is the HTTP API boundary for the system
- it owns the canonical Java-side event contract and queue topology
- it persists metadata and job state

## `workers/`

Purpose: Python implementation of the asynchronous processing stages.

Important files:

- [`worker_base.py`](../workers/worker_base.py): common RabbitMQ, PostgreSQL, MinIO, logging, ACK/NACK handling
- [`normalization_worker.py`](../workers/normalization_worker.py): FFmpeg normalization stage
- [`transcription_worker.py`](../workers/transcription_worker.py): Whisper transcription stage
- [`requirements.txt`](../workers/requirements.txt): Python dependencies
- [`README.md`](../workers/README.md): worker-specific setup notes
- `.env`, `.env.example`: worker configuration
- `*.log`, `*.pid`: runtime artifacts committed/present in the repo
- `.venv/`, `venv/`: local virtual environments present in the working tree

Why it exists:

- Spring Boot is used for ingestion and query APIs
- Python is used where media tooling and ML libraries are simpler to integrate

## `scripts/`

Purpose: convenience shell scripts for local operations.

Files:

- `start-pipeline.sh`: starts Docker infra and prepares Python venv
- `start-services.sh`: legacy direct `docker run` startup for MinIO and RabbitMQ
- `start-everything.sh`: starts infra, API, workers, then launches JMeter
- `run-python-workers.sh`: boots workers in background and records PIDs
- `stop-python-workers.sh`: stops worker PIDs
- `stop-services.sh`: stops MinIO and RabbitMQ containers
- `stop-everything.sh`: stops workers, API, then `docker-compose down`

Why it exists:

- the repo does not include a unified process manager
- scripts are the local operational interface

## `images/`

Purpose: screenshots used in the README and benchmark narrative.

Contents:

- synchronous summary report screenshot
- second-iteration screenshots for API response, MinIO files, RabbitMQ dashboard, and summary report

Why it exists:

- the repo is partly an architecture/performance demonstration

## `flow/`

Purpose: older architecture notes and implementation status documents.

Important caveat:

- these files are useful for intent but are not always authoritative
- some statements in `flow/` describe listeners or behaviors that are not implemented in the Spring app

Why it exists:

- the repository appears to have been developed incrementally with design notes preserved in-tree

## `runtime/`

Purpose: local runtime state for the Spring Boot API when launched by scripts.

Files:

- `api.log`
- `api.pid`

Why it exists:

- `scripts/start-everything.sh` backgrounds the API and writes process metadata here

## `jmeter/`

Purpose: extra JMeter log output from previous runs.

Why it exists:

- local load-testing artifacts were kept in the repo

## `docs/`

Purpose: reverse-engineered onboarding and maintenance documentation added during this pass.

## Non-source folders that still matter

### `AudioPipeline/target/`

Not source, but useful for:

- confirming what was compiled
- inspecting previous test output
- seeing that tests have been run locally

### `workers/.venv/` and `workers/venv/`

Not source, but they reveal:

- local Python versions used (`3.9` and `3.12` appear)
- that the repo currently carries environment clutter
- the scripts standardize on `workers/venv`

## Repo Hygiene Summary

This repository mixes:

- source code
- infra definitions
- test assets
- logs
- PID files
- local venvs
- build output
- older notes
- committed secrets

That mix is important for maintainers because it affects reproducibility, security posture, and onboarding confusion.
