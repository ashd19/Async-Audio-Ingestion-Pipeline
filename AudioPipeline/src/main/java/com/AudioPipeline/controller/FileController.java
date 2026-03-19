package com.AudioPipeline.controller;

import com.AudioPipeline.dto.AudioFileDto;
import com.AudioPipeline.service.AudioFileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class FileController {
    private final AudioFileService audioFileService;

    public FileController(AudioFileService audioFileService) {
        this.audioFileService = audioFileService;
    }

    @PostMapping("/upload")
    public ResponseEntity<AudioFileDto> uploadFile(@Valid @RequestBody AudioFileDto audioFileDto,
                                                   HttpServletRequest request) {
        AudioFileDto saved = audioFileService.addFilePath(audioFileDto, request.getRemoteAddr());
        return ResponseEntity.ok(saved);
    }
}
