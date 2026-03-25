#!/usr/bin/env python3
import os
import tempfile
import whisper
import psycopg2
from typing import Dict, Any
from worker_base import WorkerBase

class TranscriptionWorker(WorkerBase):
    """Worker for audio transcription using OpenAI Whisper."""
    
    def __init__(self):
        queue_name = os.getenv('TRANSCRIPTION_QUEUE', 'audio.transcription.queue')
        super().__init__('TranscriptionWorker', queue_name)
        
        # Load Whisper model (first run downloads ~3GB base model)
        model_size = os.getenv('WHISPER_MODEL', 'base')
        self.logger.info(f"Loading Whisper model: {model_size}", extra={'traceId': 'init'})
        self.model = whisper.load_model(model_size)
        self.logger.info("Whisper model loaded", extra={'traceId': 'init'})
        
    def transcribe_audio(self, audio_path: str, trace_id: str) -> Dict[str, Any]:
        """Transcribe audio using Whisper."""
        try:
            self.logger.info(f"Transcribing audio: {audio_path}", extra={'traceId': trace_id})
            
            result = self.model.transcribe(
                audio_path,
                fp16=False,  # Use FP32 for CPU compatibility
                verbose=False
            )
            
            transcript_data = {
                'text': result['text'].strip(),
                'language': result['language'],
                'segments': result['segments']
            }
            
            self.logger.info(
                f"Transcription completed. Language: {result['language']}, "
                f"Length: {len(result['text'])} chars",
                extra={'traceId': trace_id}
            )
            
            return transcript_data
            
        except Exception as e:
            self.logger.error(f"Whisper transcription failed: {e}", extra={'traceId': trace_id})
            return None
    
    def save_transcript(self, audio_file_id: int, job_id: int, 
                       transcript_data: Dict[str, Any], trace_id: str) -> bool:
        """Save transcript to database."""
        try:
            cursor = self.db_connection.cursor()
            
            # Calculate average confidence from segments
            segments = transcript_data.get('segments', [])
            if segments:
                avg_confidence = sum(seg.get('no_speech_prob', 0) for seg in segments) / len(segments)
                confidence = 1.0 - avg_confidence  # Invert no_speech_prob
            else:
                confidence = None
            
            sql = """
                INSERT INTO transcripts (audio_file_id, job_id, transcript_text, language, confidence, created_at)
                VALUES (%s, %s, %s, %s, %s, CURRENT_TIMESTAMP)
            """
            
            cursor.execute(sql, (
                audio_file_id,
                job_id,
                transcript_data['text'],
                transcript_data['language'],
                confidence
            ))
            
            self.db_connection.commit()
            cursor.close()
            
            self.logger.info(f"Transcript saved for audio {audio_file_id}", extra={'traceId': trace_id})
            return True
            
        except Exception as e:
            self.db_connection.rollback()
            self.logger.error(f"Failed to save transcript: {e}", extra={'traceId': trace_id})
            return False
    
    def process_message(self, event: Dict[str, Any], trace_id: str) -> bool:
        """Process normalized audio event and transcribe it."""
        temp_audio = None
        
        try:
            payload = event.get('payload', {})
            audio_file_id = payload.get('audioFileId')
            job_id = payload.get('jobId')
            object_key = payload.get('objectKey')
            
            if not all([audio_file_id, job_id, object_key]):
                self.logger.error(f"Missing required fields in payload", extra={'traceId': trace_id})
                return False
            
            # Idempotency check
            if not self.check_idempotency(job_id, 'TRANSCRIPTION', trace_id):
                return True  # Already processed, ACK it
            
            # Update status to PROCESSING
            if not self.update_job_status(job_id, 'TRANSCRIPTION', 'PROCESSING', trace_id=trace_id):
                return False
            
            # Download normalized audio from MinIO
            temp_audio = tempfile.NamedTemporaryFile(delete=False, suffix='.mp3')
            temp_audio.close()
            
            if not self.download_from_minio(object_key, temp_audio.name, trace_id):
                self.update_job_status(
                    job_id, 'TRANSCRIPTION', 'FAILED',
                    error_code='DOWNLOAD_FAILED',
                    error_message='Failed to download audio from storage',
                    trace_id=trace_id
                )
                return False
            
            # Transcribe audio
            transcript_data = self.transcribe_audio(temp_audio.name, trace_id)
            
            if not transcript_data:
                self.update_job_status(
                    job_id, 'TRANSCRIPTION', 'FAILED',
                    error_code='TRANSCRIPTION_FAILED',
                    error_message='Whisper transcription failed',
                    trace_id=trace_id
                )
                return False
            
            # Save transcript to database
            if not self.save_transcript(audio_file_id, job_id, transcript_data, trace_id):
                self.update_job_status(
                    job_id, 'TRANSCRIPTION', 'FAILED',
                    error_code='SAVE_FAILED',
                    error_message='Failed to save transcript to database',
                    trace_id=trace_id
                )
                return False
            
            # Update job status to COMPLETED
            transcript_key = f"transcripts/{audio_file_id}.txt"
            if not self.update_job_status(
                job_id, 'TRANSCRIPTION', 'COMPLETED',
                object_key=transcript_key,
                trace_id=trace_id
            ):
                return False
            
            # Publish audio.transcribed event
            next_payload = {
                'audioFileId': audio_file_id,
                'jobId': job_id,
                'objectKey': transcript_key
            }
            
            routing_key = os.getenv('TRANSCRIBED_ROUTING_KEY', 'audio.transcribed')
            if not self.publish_event('AUDIO_TRANSCRIBED', next_payload, routing_key, trace_id):
                return False
            
            self.logger.info(
                f"Transcription completed for job {job_id}",
                extra={'traceId': trace_id}
            )
            return True
            
        except Exception as e:
            self.logger.error(f"Process message failed: {e}", extra={'traceId': trace_id})
            return False
            
        finally:
            # Cleanup temporary files
            if temp_audio and os.path.exists(temp_audio.name):
                os.unlink(temp_audio.name)

if __name__ == '__main__':
    worker = TranscriptionWorker()
    worker.start()
