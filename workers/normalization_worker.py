#!/usr/bin/env python3
import os
import tempfile
import ffmpeg
from typing import Dict, Any
from worker_base import WorkerBase

class NormalizationWorker(WorkerBase):
    """Worker for audio normalization using FFmpeg."""
    
    def __init__(self):
        queue_name = os.getenv('NORMALIZATION_QUEUE', 'audio.normalization.queue')
        super().__init__('NormalizationWorker', queue_name)
        
    def normalize_audio(self, input_path: str, output_path: str, trace_id: str) -> bool:
        """Normalize audio using FFmpeg loudnorm filter."""
        try:
            self.logger.info(f"Normalizing audio: {input_path}", extra={'traceId': trace_id})
            
            # Use FFmpeg loudnorm filter for audio normalization
            # Target: -16 LUFS (standard for streaming platforms)
            stream = ffmpeg.input(input_path)
            stream = ffmpeg.filter(stream, 'loudnorm', I=-16, TP=-1.5, LRA=11)
            stream = ffmpeg.output(stream, output_path, 
                                 acodec='libmp3lame',
                                 audio_bitrate='192k',
                                 ar=44100)
            
            ffmpeg.run(stream, capture_stdout=True, capture_stderr=True, overwrite_output=True)
            
            self.logger.info(f"Audio normalized successfully", extra={'traceId': trace_id})
            return True
            
        except ffmpeg.Error as e:
            self.logger.error(
                f"FFmpeg normalization failed: {e.stderr.decode() if e.stderr else str(e)}",
                extra={'traceId': trace_id}
            )
            return False
        except Exception as e:
            self.logger.error(f"Normalization error: {e}", extra={'traceId': trace_id})
            return False
    
    def process_message(self, event: Dict[str, Any], trace_id: str) -> bool:
        """Process uploaded audio event and normalize it."""
        temp_input = None
        temp_output = None
        
        try:
            payload = event.get('payload', {})
            audio_file_id = payload.get('audioFileId')
            job_id = payload.get('jobId')
            object_key = payload.get('objectKey')
            
            if not all([audio_file_id, job_id, object_key]):
                self.logger.error(f"Missing required fields in payload", extra={'traceId': trace_id})
                return False
            
            # Idempotency check
            if not self.check_idempotency(job_id, 'NORMALIZATION', trace_id):
                return True  # Already processed, ACK it
            
            # Update status to PROCESSING
            if not self.update_job_status(job_id, 'NORMALIZATION', 'PROCESSING', trace_id=trace_id):
                return False
            
            # Download raw audio from MinIO
            temp_input = tempfile.NamedTemporaryFile(delete=False, suffix='.mp3')
            temp_input.close()
            
            if not self.download_from_minio(object_key, temp_input.name, trace_id):
                self.update_job_status(
                    job_id, 'NORMALIZATION', 'FAILED',
                    error_code='DOWNLOAD_FAILED',
                    error_message='Failed to download audio from storage',
                    trace_id=trace_id
                )
                return False
            
            # Normalize audio
            temp_output = tempfile.NamedTemporaryFile(delete=False, suffix='_normalized.mp3')
            temp_output.close()
            
            if not self.normalize_audio(temp_input.name, temp_output.name, trace_id):
                self.update_job_status(
                    job_id, 'NORMALIZATION', 'FAILED',
                    error_code='NORMALIZATION_FAILED',
                    error_message='FFmpeg normalization failed',
                    trace_id=trace_id
                )
                return False
            
            # Upload normalized audio back to MinIO
            normalized_key = f"normalized/{audio_file_id}.mp3"
            if not self.upload_to_minio(temp_output.name, normalized_key, trace_id):
                self.update_job_status(
                    job_id, 'NORMALIZATION', 'FAILED',
                    error_code='UPLOAD_FAILED',
                    error_message='Failed to upload normalized audio to storage',
                    trace_id=trace_id
                )
                return False
            
            # Update job status to COMPLETED
            if not self.update_job_status(
                job_id, 'NORMALIZATION', 'COMPLETED',
                object_key=normalized_key,
                trace_id=trace_id
            ):
                return False
            
            # Publish audio.normalized event
            next_payload = {
                'audioFileId': audio_file_id,
                'jobId': job_id,
                'objectKey': normalized_key
            }
            
            routing_key = os.getenv('NORMALIZED_ROUTING_KEY', 'audio.normalized')
            if not self.publish_event('AUDIO_NORMALIZED', next_payload, routing_key, trace_id):
                return False
            
            self.logger.info(
                f"Normalization completed for job {job_id}",
                extra={'traceId': trace_id}
            )
            return True
            
        except Exception as e:
            self.logger.error(f"Process message failed: {e}", extra={'traceId': trace_id})
            return False
            
        finally:
            # Cleanup temporary files
            if temp_input and os.path.exists(temp_input.name):
                os.unlink(temp_input.name)
            if temp_output and os.path.exists(temp_output.name):
                os.unlink(temp_output.name)

if __name__ == '__main__':
    worker = NormalizationWorker()
    worker.start()
