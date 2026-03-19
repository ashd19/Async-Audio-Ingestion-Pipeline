package com.AudioPipeline.repository;

import com.AudioPipeline.entity.AudioFilesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AudioFileRepository extends JpaRepository<AudioFilesEntity, Long> {
}
