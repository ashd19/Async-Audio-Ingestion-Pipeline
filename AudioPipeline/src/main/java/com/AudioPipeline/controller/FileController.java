package com.AudioPipeline.controller;

import com.AudioPipeline.dto.AudioFileDto;
import com.AudioPipeline.service.AudioFileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@RestController
@RequestMapping("/api")
public class FileController {
    private final AudioFileService audioFileService;

    public FileController(AudioFileService audioFileService) {
        this.audioFileService = audioFileService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AudioFileDto> uploadFile(@RequestParam("file") MultipartFile file,
                                                    HttpServletRequest request) throws Exception {
        AudioFileDto saved = audioFileService.uploadFile(file, request.getRemoteAddr());
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/files/{objectKey}")
    public ResponseEntity<InputStreamResource> getFile(@PathVariable String objectKey) throws Exception {
        AudioFileService.DownloadedFile downloadedFile = audioFileService.downloadFile(objectKey);
        InputStream stream = downloadedFile.stream();
        String contentType = downloadedFile.contentType();
        MediaType mediaType = contentType != null
                ? MediaType.parseMediaType(contentType)
                : MediaTypeFactory.getMediaType(downloadedFile.fileName()).orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.status(HttpStatus.OK)
                .contentType(mediaType)
                .contentLength(downloadedFile.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadedFile.fileName() + "\"")
                .body(new InputStreamResource(stream));
    }
}
