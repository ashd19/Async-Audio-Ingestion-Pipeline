package com.AudioPipeline.dto;

public record TranscriptDto(
        Long id,
        Long audioFileId,
        Long jobId,
        String transcriptText,
        String language,
        Double confidence
) {
}
