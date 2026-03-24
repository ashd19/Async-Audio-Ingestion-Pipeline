package com.AudioPipeline.dto;

import java.time.LocalDateTime;

public record AudioPipelineEvent(
        String eventType,
        String eventVersion,
        String eventId,
        LocalDateTime occurredAt,
        String traceId,
        AudioStagePayload payload
) {
}
