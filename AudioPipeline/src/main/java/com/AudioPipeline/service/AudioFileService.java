package com.AudioPipeline.service;

import com.AudioPipeline.dto.AudioFileDto;
import com.AudioPipeline.AudioPipeline.entity.AudioFilesEntity;
import com.AudioPipeline.repository.AudioFileRepository;
import org.springframework.stereotype.Service;

@Service
public class AudioFileService {

    private final AudioFileRepository audioFileRepository;

    public AudioFileService(AudioFileRepository audioFileRepository) {
        this.audioFileRepository = audioFileRepository;
    }

    public AudioFileDto addFilePath(AudioFileDto audioFileDto, String ipAddress) {
        AudioFilesEntity audioFilesEntity = new AudioFilesEntity();
        audioFilesEntity.setIpAddress(ipAddress);
        audioFilesEntity.setFilePath(audioFileDto.getFilePath());
        AudioFilesEntity savedEntity = audioFileRepository.save(audioFilesEntity);

        return AudioFileDto.builder()
                .filePath(savedEntity.getFilePath())
                .build();
    }

}
