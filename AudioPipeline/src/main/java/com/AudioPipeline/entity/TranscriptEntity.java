package com.AudioPipeline.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "transcripts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "audio_file_id", nullable = false)
    private Long audioFileId;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "transcript_text", columnDefinition = "TEXT")
    private String transcriptText;

    @Column(name = "language", length = 10)
    private String language;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
