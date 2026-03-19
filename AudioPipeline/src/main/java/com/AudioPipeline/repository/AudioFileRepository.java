package com.AudioPipeline.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AudioFileRepository extends JpaRepository<com.AudioPipeline.AudioPipeline.entity.AudioFilesEntity, Long> {

}
