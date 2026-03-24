package com.AudioPipeline.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AudioFileDto {
    private Long audioId;
    private Long jobId;

    @NotBlank(message = "File is required")
    @Size(max = 100, message = "File path must be at most 100 characters")
    private String filePath;

    private String stage;
    private String status;
    private String traceId;
}
