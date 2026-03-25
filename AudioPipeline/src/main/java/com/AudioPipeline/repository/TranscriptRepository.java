package com.AudioPipeline.repository;

import com.AudioPipeline.entity.TranscriptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TranscriptRepository extends JpaRepository<TranscriptEntity, Long> {
    
    Optional<TranscriptEntity> findByAudioFileId(Long audioFileId);
    
    Optional<TranscriptEntity> findByJobId(Long jobId);
}
