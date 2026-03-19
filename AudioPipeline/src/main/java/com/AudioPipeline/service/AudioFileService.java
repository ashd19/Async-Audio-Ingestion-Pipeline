package com.AudioPipeline.service;

import com.AudioPipeline.dto.AudioFileDto;
import com.AudioPipeline.entity.AudioFilesEntity;
import com.AudioPipeline.repository.AudioFileRepository;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Service
public class AudioFileService {

    private final AudioFileRepository audioFileRepository;
    private final MinioClient minioClient;
    private final String bucketName;

    public AudioFileService(AudioFileRepository audioFileRepository,
                            MinioClient minioClient,
                            com.AudioPipeline.Configuration.MinioConfig minioConfig) {
        this.audioFileRepository = audioFileRepository;
        this.minioClient = minioClient;
        this.bucketName = minioConfig.getBucket();
    }

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

        return AudioFileDto.builder()
                .filePath(savedEntity.getFilePath())
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
}
