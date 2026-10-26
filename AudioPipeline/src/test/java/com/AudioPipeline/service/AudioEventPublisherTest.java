package com.AudioPipeline.service;

import com.AudioPipeline.Configuration.RabbitMqProperties;
import com.AudioPipeline.dto.AudioPipelineEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AudioEventPublisherTest {

    @Mock RabbitTemplate rabbitTemplate;

    @Test
    void publishUploadedEvent_sendsVersionedEventToConfiguredExchangeAndRoutingKey() {
        RabbitMqProperties props = new RabbitMqProperties();
        props.setExchange("audio.exchange");
        props.setUploadedRoutingKey("audio.uploaded");
        AudioEventPublisher publisher = new AudioEventPublisher(rabbitTemplate, props);

        publisher.publishUploadedEvent(7L, 42L, "abc-file.mp3", "trace-1");

        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq("audio.exchange"), eq("audio.uploaded"), body.capture());
        AudioPipelineEvent event = (AudioPipelineEvent) body.getValue();
        assertThat(event.eventType()).isEqualTo("audio.uploaded");
        assertThat(event.eventVersion()).isEqualTo("1");
        assertThat(event.traceId()).isEqualTo("trace-1");
        assertThat(event.occurredAt()).isNotNull();
        assertThat(UUID.fromString(event.eventId())).isNotNull();
        assertThat(event.payload().audioFileId()).isEqualTo(7L);
        assertThat(event.payload().jobId()).isEqualTo(42L);
        assertThat(event.payload().objectKey()).isEqualTo("abc-file.mp3");
    }

    @Test
    void publishUploadedEvent_generatesUniqueEventIdPerCall() {
        RabbitMqProperties props = new RabbitMqProperties();
        props.setExchange("x");
        props.setUploadedRoutingKey("k");
        AudioEventPublisher publisher = new AudioEventPublisher(rabbitTemplate, props);

        publisher.publishUploadedEvent(1L, 1L, "o", "t");
        publisher.publishUploadedEvent(1L, 1L, "o", "t");

        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate, org.mockito.Mockito.times(2)).convertAndSend(eq("x"), eq("k"), body.capture());
        AudioPipelineEvent first = (AudioPipelineEvent) body.getAllValues().get(0);
        AudioPipelineEvent second = (AudioPipelineEvent) body.getAllValues().get(1);
        assertThat(first.eventId()).isNotEqualTo(second.eventId());
    }
}
