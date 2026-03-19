package com.AudioPipeline.controller;

import com.AudioPipeline.dto.AudioFileDto;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class FileController {
    private AudioFileDto audioFileDto;

//    @PostMapping("/upload")
//    public ResponseEntity<AudioFileDto> uploadFile(@RequestBody AudioFileDto audioFileDto) {
//        // save a json string to db ...
//    }

    @GetMapping("/upload")
    public String upload(){
        return "upload";
    }
}