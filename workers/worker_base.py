import json
import logging
import os
import time
import uuid
from abc import ABC, abstractmethod
from datetime import datetime
from typing import Dict, Any, Optional

import pika
import psycopg2
from minio import Minio
from minio.error import S3Error
from dotenv import load_dotenv
from pythonjsonlogger import jsonlogger

load_dotenv()

class WorkerBase(ABC):
    """Base class for all audio pipeline workers with common functionality."""
    
    def __init__(self, worker_name: str, queue_name: str):
        self.worker_name = worker_name
        self.queue_name = queue_name
        
        # Setup structured logging
        self.logger = self._setup_logger()
        
        # Configuration
        self.max_retries = int(os.getenv('MAX_RETRIES', '3'))
        self.retry_backoff = int(os.getenv('RETRY_BACKOFF_SECONDS', '5'))
        
        # Initialize connections
        self.rabbitmq_connection = None
        self.rabbitmq_channel = None
        self.minio_client = None
        self.db_connection = None
        
        self._connect_services()
        
    def _setup_logger(self) -> logging.Logger:
        """Setup JSON structured logging with traceId support."""
        logger = logging.getLogger(self.worker_name)
        log_level = os.getenv('LOG_LEVEL', 'INFO')
        logger.setLevel(getattr(logging, log_level))
        
        handler = logging.StreamHandler()
        formatter = jsonlogger.JsonFormatter(
            '%(asctime)s %(name)s %(levelname)s %(message)s %(traceId)s'
        )
        handler.setFormatter(formatter)
        logger.addHandler(handler)
        
        return logger
    
    def _connect_services(self):
        """Initialize connections to RabbitMQ, MinIO, and PostgreSQL."""
        try:
            # RabbitMQ
            self.logger.info(f"Connecting to RabbitMQ...", extra={'traceId': 'init'})
            credentials = pika.PlainCredentials(
                os.getenv('RABBITMQ_USER', 'guest'),
                os.getenv('RABBITMQ_PASSWORD', 'guest')
            )
            parameters = pika.ConnectionParameters(
                host=os.getenv('RABBITMQ_HOST', 'localhost'),
                port=int(os.getenv('RABBITMQ_PORT', '5672')),
                virtual_host=os.getenv('RABBITMQ_VHOST', '/'),
                credentials=credentials,
                heartbeat=600,
                blocked_connection_timeout=300
            )
            self.rabbitmq_connection = pika.BlockingConnection(parameters)
            self.rabbitmq_channel = self.rabbitmq_connection.channel()
            self.rabbitmq_channel.basic_qos(prefetch_count=1)
            
            # MinIO
            self.logger.info(f"Connecting to MinIO...", extra={'traceId': 'init'})
            self.minio_client = Minio(
                os.getenv('MINIO_ENDPOINT', 'localhost:9000'),
                access_key=os.getenv('MINIO_ACCESS_KEY', 'minioadmin'),
                secret_key=os.getenv('MINIO_SECRET_KEY', 'minioadmin'),
                secure=os.getenv('MINIO_SECURE', 'false').lower() == 'true'
            )
            
            # PostgreSQL
            self.logger.info(f"Connecting to PostgreSQL...", extra={'traceId': 'init'})
            self.db_connection = psycopg2.connect(
                host=os.getenv('DB_HOST', 'localhost'),
                port=int(os.getenv('DB_PORT', '5432')),
                database=os.getenv('DB_NAME', 'audiopipeline'),
                user=os.getenv('DB_USER', 'postgres'),
                password=os.getenv('DB_PASSWORD', 'postgres')
            )
            self.db_connection.autocommit = False
            
            self.logger.info(f"{self.worker_name} initialized successfully", extra={'traceId': 'init'})
            
        except Exception as e:
            self.logger.error(f"Failed to initialize services: {e}", extra={'traceId': 'init'})
            raise
    
    def download_from_minio(self, object_key: str, local_path: str, trace_id: str) -> bool:
        """Download file from MinIO to local path."""
        try:
            bucket = os.getenv('MINIO_BUCKET', 'audio-files')
            self.logger.info(f"Downloading {object_key} from MinIO", extra={'traceId': trace_id})
            self.minio_client.fget_object(bucket, object_key, local_path)
            self.logger.info(f"Downloaded {object_key} successfully", extra={'traceId': trace_id})
            return True
        except S3Error as e:
            self.logger.error(f"MinIO download failed: {e}", extra={'traceId': trace_id})
            return False
    
    def upload_to_minio(self, local_path: str, object_key: str, trace_id: str) -> bool:
        """Upload file from local path to MinIO."""
        try:
            bucket = os.getenv('MINIO_BUCKET', 'audio-files')
            self.logger.info(f"Uploading {object_key} to MinIO", extra={'traceId': trace_id})
            self.minio_client.fput_object(bucket, object_key, local_path)
            self.logger.info(f"Uploaded {object_key} successfully", extra={'traceId': trace_id})
            return True
        except S3Error as e:
            self.logger.error(f"MinIO upload failed: {e}", extra={'traceId': trace_id})
            return False
    
    def update_job_status(self, job_id: int, stage: str, status: str, 
                          object_key: Optional[str] = None, 
                          error_code: Optional[str] = None,
                          error_message: Optional[str] = None,
                          trace_id: str = None) -> bool:
        """Update job status in database."""
        try:
            cursor = self.db_connection.cursor()
            
            if status == 'PROCESSING':
                sql = """
                    UPDATE audio_processing_jobs 
                    SET stage = %s, status = %s, started_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                    WHERE id = %s
                """
                cursor.execute(sql, (stage, status, job_id))
            elif status == 'COMPLETED':
                if object_key:
                    sql = """
                        UPDATE audio_processing_jobs 
                        SET stage = %s, status = %s, object_key = %s, 
                            completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                        WHERE id = %s
                    """
                    cursor.execute(sql, (stage, status, object_key, job_id))
                else:
                    sql = """
                        UPDATE audio_processing_jobs 
                        SET stage = %s, status = %s, completed_at = CURRENT_TIMESTAMP, 
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = %s
                    """
                    cursor.execute(sql, (stage, status, job_id))
            elif status == 'FAILED':
                sql = """
                    UPDATE audio_processing_jobs 
                    SET status = %s, error_code = %s, error_message = %s, 
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = %s
                """
                cursor.execute(sql, (status, error_code, error_message, job_id))
            else:
                sql = """
                    UPDATE audio_processing_jobs 
                    SET stage = %s, status = %s, updated_at = CURRENT_TIMESTAMP
                    WHERE id = %s
                """
                cursor.execute(sql, (stage, status, job_id))
            
            self.db_connection.commit()
            cursor.close()
            self.logger.info(f"Updated job {job_id} to {stage}/{status}", extra={'traceId': trace_id})
            return True
            
        except Exception as e:
            self.db_connection.rollback()
            self.logger.error(f"Failed to update job status: {e}", extra={'traceId': trace_id})
            return False
    
    def publish_event(self, event_type: str, payload: Dict[str, Any], 
                      routing_key: str, trace_id: str) -> bool:
        """Publish event to RabbitMQ exchange."""
        try:
            event = {
                'eventType': event_type,
                'eventVersion': '1.0',
                'eventId': str(uuid.uuid4()),
                'occurredAt': datetime.utcnow().isoformat() + 'Z',
                'traceId': trace_id,
                'payload': payload
            }
            
            exchange = os.getenv('RABBITMQ_EXCHANGE', 'audio.processing.exchange')
            
            self.rabbitmq_channel.basic_publish(
                exchange=exchange,
                routing_key=routing_key,
                body=json.dumps(event),
                properties=pika.BasicProperties(
                    delivery_mode=2,  # Persistent
                    content_type='application/json'
                )
            )
            
            self.logger.info(f"Published {event_type} event", extra={'traceId': trace_id})
            return True
            
        except Exception as e:
            self.logger.error(f"Failed to publish event: {e}", extra={'traceId': trace_id})
            return False
    
    def check_idempotency(self, job_id: int, expected_stage: str, trace_id: str) -> bool:
        """Check if job is in correct state for processing (idempotency guard)."""
        try:
            cursor = self.db_connection.cursor()
            cursor.execute(
                "SELECT stage, status FROM audio_processing_jobs WHERE id = %s",
                (job_id,)
            )
            result = cursor.fetchone()
            cursor.close()
            
            if not result:
                self.logger.warning(f"Job {job_id} not found", extra={'traceId': trace_id})
                return False
            
            current_stage, current_status = result
            
            # Allow processing if previous stage completed or if retrying same stage
            if current_status == 'COMPLETED' and current_stage != expected_stage:
                return True
            elif current_stage == expected_stage and current_status in ['QUEUED', 'FAILED']:
                return True
            else:
                self.logger.info(
                    f"Skipping duplicate processing: job {job_id} at {current_stage}/{current_status}",
                    extra={'traceId': trace_id}
                )
                return False
                
        except Exception as e:
            self.logger.error(f"Idempotency check failed: {e}", extra={'traceId': trace_id})
            return False
    
    @abstractmethod
    def process_message(self, event: Dict[str, Any], trace_id: str) -> bool:
        """Process a single message. Must be implemented by subclass."""
        pass
    
    def handle_message(self, ch, method, properties, body):
        """Handle incoming RabbitMQ message with retry logic."""
        trace_id = None
        
        try:
            event = json.loads(body.decode('utf-8'))
            trace_id = event.get('traceId', 'unknown')
            
            self.logger.info(
                f"Received message: {event.get('eventType')}",
                extra={'traceId': trace_id}
            )
            
            # Process the message
            success = self.process_message(event, trace_id)
            
            if success:
                # ACK only after successful processing
                ch.basic_ack(delivery_tag=method.delivery_tag)
                self.logger.info(f"Message processed successfully", extra={'traceId': trace_id})
            else:
                # NACK and requeue for transient failures
                ch.basic_nack(delivery_tag=method.delivery_tag, requeue=True)
                self.logger.warning(f"Message processing failed, requeuing", extra={'traceId': trace_id})
                
        except json.JSONDecodeError as e:
            self.logger.error(f"Invalid JSON message: {e}", extra={'traceId': trace_id or 'unknown'})
            # Reject poison messages without requeue
            ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)
            
        except Exception as e:
            self.logger.error(f"Unexpected error: {e}", extra={'traceId': trace_id or 'unknown'})
            # NACK and requeue
            ch.basic_nack(delivery_tag=method.delivery_tag, requeue=True)
    
    def start(self):
        """Start consuming messages from the queue."""
        try:
            self.logger.info(f"Starting {self.worker_name} consumer on queue: {self.queue_name}")
            
            self.rabbitmq_channel.basic_consume(
                queue=self.queue_name,
                on_message_callback=self.handle_message,
                auto_ack=False
            )
            
            self.logger.info(f"{self.worker_name} is waiting for messages...")
            self.rabbitmq_channel.start_consuming()
            
        except KeyboardInterrupt:
            self.logger.info(f"Shutting down {self.worker_name}...")
            self.stop()
        except Exception as e:
            self.logger.error(f"Worker crashed: {e}")
            self.stop()
            raise
    
    def stop(self):
        """Gracefully shutdown worker."""
        try:
            if self.rabbitmq_channel and self.rabbitmq_channel.is_open:
                self.rabbitmq_channel.stop_consuming()
                self.rabbitmq_channel.close()
            
            if self.rabbitmq_connection and self.rabbitmq_connection.is_open:
                self.rabbitmq_connection.close()
            
            if self.db_connection:
                self.db_connection.close()
            
            self.logger.info(f"{self.worker_name} shutdown complete")
            
        except Exception as e:
            self.logger.error(f"Error during shutdown: {e}")
