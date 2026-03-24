package com.AudioPipeline.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record AudioJobStatusDto(
        Long jobId,
        Long audioId,
        String stage,
        String status,
        Integer retryCount,
        String objectKey,
        String traceId,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime updatedAt
) {
}
