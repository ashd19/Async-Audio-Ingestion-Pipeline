package com.AudioPipeline.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@EnableConfigurationProperties(RabbitMqProperties.class)
public class RabbitMqConfig {

    @Bean
    TopicExchange audioProcessingExchange(RabbitMqProperties properties) {
        return new TopicExchange(properties.getExchange(), true, false);
    }

    @Bean
    DirectExchange deadLetterExchange(RabbitMqProperties properties) {
        return new DirectExchange(properties.getDeadLetterExchange(), true, false);
    }

    @Bean
    Queue normalizationQueue(RabbitMqProperties properties) {
        return new Queue(
                properties.getNormalizationQueue(),
                true,
                false,
                false,
                Map.of(
                        "x-dead-letter-exchange", properties.getDeadLetterExchange(),
                        "x-dead-letter-routing-key", properties.getNormalizationDlq()
                )
        );
    }

    @Bean
    Queue transcriptionQueue(RabbitMqProperties properties) {
        return new Queue(
                properties.getTranscriptionQueue(),
                true,
                false,
                false,
                Map.of(
                        "x-dead-letter-exchange", properties.getDeadLetterExchange(),
                        "x-dead-letter-routing-key", properties.getTranscriptionDlq()
                )
        );
    }

    @Bean
    Queue embeddingQueue(RabbitMqProperties properties) {
        return new Queue(
                properties.getEmbeddingQueue(),
                true,
                false,
                false,
                Map.of(
                        "x-dead-letter-exchange", properties.getDeadLetterExchange(),
                        "x-dead-letter-routing-key", properties.getEmbeddingDlq()
                )
        );
    }

    @Bean
    Queue normalizationDlq(RabbitMqProperties properties) {
        return new Queue(properties.getNormalizationDlq(), true);
    }

    @Bean
    Queue transcriptionDlq(RabbitMqProperties properties) {
        return new Queue(properties.getTranscriptionDlq(), true);
    }

    @Bean
    Queue embeddingDlq(RabbitMqProperties properties) {
        return new Queue(properties.getEmbeddingDlq(), true);
    }

    @Bean
    Binding uploadToNormalizationBinding(RabbitMqProperties properties,
                                         TopicExchange audioProcessingExchange,
                                         Queue normalizationQueue) {
        return BindingBuilder.bind(normalizationQueue)
                .to(audioProcessingExchange)
                .with(properties.getUploadedRoutingKey());
    }

    @Bean
    Binding normalizedToTranscriptionBinding(RabbitMqProperties properties,
                                             TopicExchange audioProcessingExchange,
                                             Queue transcriptionQueue) {
        return BindingBuilder.bind(transcriptionQueue)
                .to(audioProcessingExchange)
                .with(properties.getNormalizedRoutingKey());
    }

    @Bean
    Binding transcribedToEmbeddingBinding(RabbitMqProperties properties,
                                          TopicExchange audioProcessingExchange,
                                          Queue embeddingQueue) {
        return BindingBuilder.bind(embeddingQueue)
                .to(audioProcessingExchange)
                .with(properties.getTranscribedRoutingKey());
    }

    @Bean
    Binding normalizationDlqBinding(RabbitMqProperties properties,
                                    DirectExchange deadLetterExchange,
                                    Queue normalizationDlq) {
        return BindingBuilder.bind(normalizationDlq)
                .to(deadLetterExchange)
                .with(properties.getNormalizationDlq());
    }

    @Bean
    Binding transcriptionDlqBinding(RabbitMqProperties properties,
                                    DirectExchange deadLetterExchange,
                                    Queue transcriptionDlq) {
        return BindingBuilder.bind(transcriptionDlq)
                .to(deadLetterExchange)
                .with(properties.getTranscriptionDlq());
    }

    @Bean
    Binding embeddingDlqBinding(RabbitMqProperties properties,
                                DirectExchange deadLetterExchange,
                                Queue embeddingDlq) {
        return BindingBuilder.bind(embeddingDlq)
                .to(deadLetterExchange)
                .with(properties.getEmbeddingDlq());
    }

    @Bean
    MessageConverter messageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
