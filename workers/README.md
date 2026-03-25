# Audio Pipeline Python Workers

This directory contains standalone Python workers for audio processing stages.

## Setup

### 1. Create Virtual Environment

```bash
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
```

### 2. Install Dependencies

```bash
pip install -r requirements.txt
```

### 3. Install FFmpeg

Workers require FFmpeg for audio normalization.

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install ffmpeg
```

**macOS:**
```bash
brew install ffmpeg
```

**Windows:**
Download from https://ffmpeg.org/download.html

### 4. Configure Environment

Create a `.env` file in the `workers/` directory with configuration values.

## Running Workers

### Normalization Worker
```bash
python normalization_worker.py
```

### Transcription Worker
```bash
python transcription_worker.py
```

## Architecture

Each worker:
- Consumes messages from a dedicated RabbitMQ queue
- Downloads audio files from MinIO
- Processes audio (normalize/transcribe)
- Uploads results back to MinIO/PostgreSQL
- Publishes next-stage events
- Implements manual ACK with retry logic
- Handles idempotency and error recovery
