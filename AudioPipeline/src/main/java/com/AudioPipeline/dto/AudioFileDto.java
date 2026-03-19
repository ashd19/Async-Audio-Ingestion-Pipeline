package com.AudioPipeline.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class AudioFileDto {
    // User uploads a file that is stored locally and its path is persisted to DB.
    @NotBlank(message = "File is required")
    @Size(max = 100, message = "File path must be at most 100 characters")
    private String filePath;

}
