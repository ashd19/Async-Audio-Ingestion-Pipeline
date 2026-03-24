package com.AudioPipeline.repository;

import com.AudioPipeline.entity.AudioProcessingJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AudioProcessingJobRepository extends JpaRepository<AudioProcessingJobEntity, Long> {
}
