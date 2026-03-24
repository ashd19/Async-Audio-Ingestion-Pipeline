package com.AudioPipeline.Configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rabbitmq")
public class RabbitMqProperties {
    private String exchange;
    private String normalizationQueue;
    private String transcriptionQueue;
    private String embeddingQueue;
    private String normalizationDlq;
    private String transcriptionDlq;
    private String embeddingDlq;
    private String deadLetterExchange;
    private String uploadedRoutingKey;
    private String normalizedRoutingKey;
    private String transcribedRoutingKey;
    private String embeddedRoutingKey;

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getNormalizationQueue() {
        return normalizationQueue;
    }

    public void setNormalizationQueue(String normalizationQueue) {
        this.normalizationQueue = normalizationQueue;
    }

    public String getTranscriptionQueue() {
        return transcriptionQueue;
    }

    public void setTranscriptionQueue(String transcriptionQueue) {
        this.transcriptionQueue = transcriptionQueue;
    }

    public String getEmbeddingQueue() {
        return embeddingQueue;
    }

    public void setEmbeddingQueue(String embeddingQueue) {
        this.embeddingQueue = embeddingQueue;
    }

    public String getNormalizationDlq() {
        return normalizationDlq;
    }

    public void setNormalizationDlq(String normalizationDlq) {
        this.normalizationDlq = normalizationDlq;
    }

    public String getTranscriptionDlq() {
        return transcriptionDlq;
    }

    public void setTranscriptionDlq(String transcriptionDlq) {
        this.transcriptionDlq = transcriptionDlq;
    }

    public String getEmbeddingDlq() {
        return embeddingDlq;
    }

    public void setEmbeddingDlq(String embeddingDlq) {
        this.embeddingDlq = embeddingDlq;
    }

    public String getDeadLetterExchange() {
        return deadLetterExchange;
    }

    public void setDeadLetterExchange(String deadLetterExchange) {
        this.deadLetterExchange = deadLetterExchange;
    }

    public String getUploadedRoutingKey() {
        return uploadedRoutingKey;
    }

    public void setUploadedRoutingKey(String uploadedRoutingKey) {
        this.uploadedRoutingKey = uploadedRoutingKey;
    }

    public String getNormalizedRoutingKey() {
        return normalizedRoutingKey;
    }

    public void setNormalizedRoutingKey(String normalizedRoutingKey) {
        this.normalizedRoutingKey = normalizedRoutingKey;
    }

    public String getTranscribedRoutingKey() {
        return transcribedRoutingKey;
    }

    public void setTranscribedRoutingKey(String transcribedRoutingKey) {
        this.transcribedRoutingKey = transcribedRoutingKey;
    }

    public String getEmbeddedRoutingKey() {
        return embeddedRoutingKey;
    }

    public void setEmbeddedRoutingKey(String embeddedRoutingKey) {
        this.embeddedRoutingKey = embeddedRoutingKey;
    }
}
