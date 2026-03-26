# Async Audio Ingestion Pipeline

A production-style asynchronous audio processing system built to demonstrate scalable backend architecture, not just file upload + transcription.

## Overview

This project is designed around an event-driven workflow where audio is uploaded, queued, and processed by background workers in stages.  
The API stays responsive while heavy processing happens asynchronously.

### Why this project

Traditional synchronous processing can block request threads and fail under load.  
This pipeline separates responsibilities so the system can scale, recover from failures, and evolve stage by stage.

## 🚀 Getting Started

### 1. Initial Setup
Run the comprehensive setup script to start Docker infrastructure and prepare the Python environment:
```bash
./start-pipeline.sh
```

### 2. Run the API
In a new terminal:
```bash
cd AudioPipeline && ./mvnw spring-boot:run
```

### 3. Run the Python Workers
Start both normalization and transcription workers in the background:
```bash
./run-python-workers.sh
```
- **View logs:** `tail -f workers/*.log`
- **Stop workers:** `./stop-python-workers.sh`

### 4. Test the Pipeline
```bash
curl -X POST http://localhost:8080/api/upload -F "file=@your_audio.mp3"
```

## 🛠️ Tech Stack

- **Backend:** Spring Boot  
- **Database:** PostgreSQL (NeonDB)  
- **Object Storage:** MinIO (Docker)  
- **Load Testing:** Apache JMeter

## 🧪 Test Setup

- **Threads (users):** 150  
- **Ramp-up period:** 2 seconds  
- **Loop count:** 10  
- **Audio file size:** ~5MB



<!--
## Architecture Summary

The pipeline follows an asynchronous event flow:

`audio.uploaded` → `audio.normalized` → `audio.transcribed` → `audio.embedded`

At each stage, a worker:

1. Consumes the incoming event
2. Processes the audio/job payload
3. Persists artifacts/results
4. Publishes the next event

This design enables:

- loose coupling between services
- horizontal worker scaling
- better fault isolation and retries
- non-blocking API behavior

## Core Design Principles

- **Asynchronous Processing:** No heavy processing in API request/response path
- **Event-Driven Communication:** Services communicate through events, not tight direct calls
- **Scalability:** Workers can scale independently based on queue depth and workload
- **Fault Tolerance:** Failures are isolated; retries and DLQ patterns can be introduced per stage

## What this demonstrates

- Distributed backend system design
- Message-driven workflow orchestration
- Handling large file workloads without blocking APIs
- Real-world architecture thinking beyond CRUD applications

## Future Enhancements

- Real-time/streaming audio ingestion
- Observability (metrics, tracing, dashboards)
- Semantic search over transcriptions/embeddings
- Multi-tenant processing and workload isolation
-->
