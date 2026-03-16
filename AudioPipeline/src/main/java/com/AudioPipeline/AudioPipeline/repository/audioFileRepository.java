package com.AudioPipeline.AudioPipeline.repository;

import com.AudioPipeline.AudioPipeline.entity.audioFilesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface audioFileRepository extends JpaRepository<audioFilesEntity , Long> {

}
