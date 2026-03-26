package com.AudioPipeline.service;

import com.AudioPipeline.Configuration.RabbitMqProperties;
import com.AudioPipeline.dto.AudioPipelineEvent;
import com.AudioPipeline.dto.AudioStagePayload;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AudioEventPublisher {

    private static final String EVENT_VERSION = "1";

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMqProperties rabbitMqProperties;

    public AudioEventPublisher(RabbitTemplate rabbitTemplate, RabbitMqProperties rabbitMqProperties) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitMqProperties = rabbitMqProperties;
    }

    public void publishUploadedEvent(Long audioFileId, Long jobId, String objectKey, String traceId) {
        publish("audio.uploaded", rabbitMqProperties.getUploadedRoutingKey(), audioFileId, jobId, objectKey, traceId);
    }

    private void publish(String eventType, String routingKey, Long audioFileId, Long jobId, String objectKey, String traceId) {
        AudioPipelineEvent event = new AudioPipelineEvent(
                eventType,
                EVENT_VERSION,
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                traceId,
                new AudioStagePayload(audioFileId, jobId, objectKey)
        );

        rabbitTemplate.convertAndSend(
                rabbitMqProperties.getExchange(),
                routingKey,
                event
        );
    }
}
