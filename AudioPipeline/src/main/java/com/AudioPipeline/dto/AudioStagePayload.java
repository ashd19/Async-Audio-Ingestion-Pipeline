package com.AudioPipeline.dto;

public record AudioStagePayload(
        Long audioFileId,
        Long jobId,
        String objectKey
) {
}
