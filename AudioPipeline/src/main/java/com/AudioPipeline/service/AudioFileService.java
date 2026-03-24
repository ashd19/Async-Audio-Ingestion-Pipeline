package com.AudioPipeline.service;

import com.AudioPipeline.dto.AudioFileDto;
import com.AudioPipeline.dto.AudioJobStatusDto;
import com.AudioPipeline.dto.AudioPipelineEvent;
import com.AudioPipeline.dto.AudioStagePayload;
import com.AudioPipeline.entity.AudioFilesEntity;
import com.AudioPipeline.entity.AudioProcessingJobEntity;
import com.AudioPipeline.repository.AudioFileRepository;
import com.AudioPipeline.repository.AudioProcessingJobRepository;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import jakarta.transaction.Transactional;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.io.InputStream;
import java.util.UUID;

@Service
public class AudioFileService {

    private final AudioFileRepository audioFileRepository;
    private final AudioProcessingJobRepository jobRepository;
    private final AudioEventPublisher audioEventPublisher;
    private final MinioClient minioClient;
    private final String bucketName;

    public AudioFileService(AudioFileRepository audioFileRepository,
                            AudioProcessingJobRepository jobRepository,
                            AudioEventPublisher audioEventPublisher,
                            MinioClient minioClient,
                            com.AudioPipeline.Configuration.MinioConfig minioConfig) {
        this.audioFileRepository = audioFileRepository;
        this.jobRepository = jobRepository;
        this.audioEventPublisher = audioEventPublisher;
        this.minioClient = minioClient;
        this.bucketName = minioConfig.getBucket();
    }

    @Transactional
    public AudioFileDto uploadFile(MultipartFile file, String ipAddress) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }

        ensureBucketExists();
        String objectName = buildObjectName(file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
        }

        AudioFilesEntity audioFilesEntity = new AudioFilesEntity();
        audioFilesEntity.setIpAddress(ipAddress);
        audioFilesEntity.setFilePath(objectName);
        AudioFilesEntity savedEntity = audioFileRepository.save(audioFilesEntity);
        String traceId = UUID.randomUUID().toString();

        AudioProcessingJobEntity job = new AudioProcessingJobEntity();
        job.setAudioFileId(savedEntity.getId());
        job.setObjectKey(savedEntity.getFilePath());
        job.setStage("UPLOAD");
        job.setStatus("QUEUED");
        job.setRetryCount(0);
        job.setTraceId(traceId);
        AudioProcessingJobEntity savedJob = jobRepository.save(job);

        audioEventPublisher.publishUploadedEvent(
                savedEntity.getId(),
                savedJob.getId(),
                savedEntity.getFilePath(),
                traceId
        );

        return AudioFileDto.builder()
                .audioId(savedEntity.getId())
                .jobId(savedJob.getId())
                .filePath(savedEntity.getFilePath())
                .stage(savedJob.getStage())
                .status(savedJob.getStatus())
                .traceId(savedJob.getTraceId())
                .build();
    }

    private void ensureBucketExists() throws Exception {
        boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!bucketExists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    private String buildObjectName(String originalFilename) {
        String safeName = originalFilename == null ? "file" : originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        return UUID.randomUUID() + "-" + safeName;
    }

    public DownloadedFile downloadFile(String objectName) throws Exception {
        if (objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException("Object key is required");
        }

        StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
        );

        GetObjectResponse objectStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
        );

        String contentType = stat.contentType();
        String fileName = objectName.contains("/") ? objectName.substring(objectName.lastIndexOf('/') + 1) : objectName;
        return new DownloadedFile(objectStream, contentType, stat.size(), fileName);
    }

    public record DownloadedFile(InputStream stream, String contentType, long size, String fileName) {
    }

    public AudioJobStatusDto getJobStatus(Long jobId) {
        AudioProcessingJobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        return AudioJobStatusDto.builder()
                .jobId(job.getId())
                .audioId(job.getAudioFileId())
                .stage(job.getStage())
                .status(job.getStatus())
                .retryCount(job.getRetryCount())
                .objectKey(job.getObjectKey())
                .traceId(job.getTraceId())
                .errorCode(job.getErrorCode())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    @Transactional
    @RabbitListener(queues = "${app.rabbitmq.normalization-queue}")
    public void onUploaded(AudioPipelineEvent event) {
        AudioStagePayload payload = event.payload();
        AudioProcessingJobEntity job = loadJob(payload.jobId());
        job.setStage("NORMALIZATION");
        job.setStatus("PROCESSING");
        if (job.getStartedAt() == null) {
            job.setStartedAt(LocalDateTime.now());
        }
        String normalizedObjectKey = "normalized/" + payload.objectKey();
        job.setObjectKey(normalizedObjectKey);
        job.setStatus("COMPLETED");
        jobRepository.save(job);
        audioEventPublisher.publishNormalizedEvent(payload.audioFileId(), job.getId(), normalizedObjectKey, job.getTraceId());
    }

    @Transactional
    @RabbitListener(queues = "${app.rabbitmq.transcription-queue}")
    public void onNormalized(AudioPipelineEvent event) {
        AudioStagePayload payload = event.payload();
        AudioProcessingJobEntity job = loadJob(payload.jobId());
        job.setStage("TRANSCRIPTION");
        job.setStatus("PROCESSING");
        String transcriptPath = "transcripts/" + payload.audioFileId() + ".txt";
        job.setObjectKey(transcriptPath);
        job.setStatus("COMPLETED");
        jobRepository.save(job);
        audioEventPublisher.publishTranscribedEvent(payload.audioFileId(), job.getId(), transcriptPath, job.getTraceId());
    }

    @Transactional
    @RabbitListener(queues = "${app.rabbitmq.embedding-queue}")
    public void onTranscribed(AudioPipelineEvent event) {
        AudioStagePayload payload = event.payload();
        AudioProcessingJobEntity job = loadJob(payload.jobId());
        job.setStage("EMBEDDING");
        job.setStatus("COMPLETED");
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);
        audioEventPublisher.publishEmbeddedEvent(payload.audioFileId(), job.getId(), payload.objectKey(), job.getTraceId());
    }

    private AudioProcessingJobEntity loadJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
    }
}
