package com.AudioPipeline.service;

import com.AudioPipeline.dto.AudioFileDto;
import com.AudioPipeline.repository.AudioFileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.AudioPipeline.AudioPipeline.entity.AudioFilesEntity;

@Service
public class AudioFileService {

    @Autowired
    private AudioFileRepository audioFileRepository;

    // save the string to db , now here entity class or dto class
    public void addFilePath(AudioFileDto audioFileDto){
        AudioFilesEntity audioFilesEntity = new AudioFilesEntity();
        audioFilesEntity.setFilePath(audioFileDto.getFilePath());
        audioFileRepository.save(audioFilesEntity);
    }

}
