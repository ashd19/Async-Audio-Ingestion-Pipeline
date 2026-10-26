package com.AudioPipeline.controller;

import com.AudioPipeline.dto.AudioFileDto;
import com.AudioPipeline.dto.AudioJobStatusDto;
import com.AudioPipeline.dto.TranscriptDto;
import com.AudioPipeline.exception.JobNotFoundException;
import com.AudioPipeline.service.AudioFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock AudioFileService service;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FileController(service)).build();
    }

    @Test
    void upload_returns202WithAbsoluteStatusUrl() throws Exception {
        AudioFileDto saved = AudioFileDto.builder()
                .audioId(7L).jobId(42L).filePath("abc-a.mp3")
                .stage("NORMALIZATION").status("QUEUED").traceId("t-1")
                .statusUrl("/api/jobs/42")
                .build();
        when(service.uploadFile(any(), any())).thenReturn(saved);
        MockMultipartFile file = new MockMultipartFile("file", "a.mp3", "audio/mpeg", new byte[]{1, 2});

        mvc.perform(multipart("/api/upload").file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.audioId").value(7))
                .andExpect(jsonPath("$.jobId").value(42))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.statusUrl").value("http://localhost/api/jobs/42"));
    }

    @Test
    void upload_withoutFilePartReturns400() throws Exception {
        mvc.perform(multipart("/api/upload"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_emptyFileReturns400WithMessage() throws Exception {
        when(service.uploadFile(any(), any())).thenThrow(new IllegalArgumentException("File is required"));
        MockMultipartFile empty = new MockMultipartFile("file", "a.mp3", "audio/mpeg", new byte[0]);

        mvc.perform(multipart("/api/upload").file(empty))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("File is required"));
    }

    @Test
    void getFile_streamsBytesWithHeaders() throws Exception {
        byte[] bytes = {9, 8, 7};
        when(service.downloadFile("normalized/7.mp3")).thenReturn(
                new AudioFileService.DownloadedFile(new ByteArrayInputStream(bytes), "audio/mpeg", 3, "7.mp3"));

        mvc.perform(get("/api/files/normalized/7.mp3"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"7.mp3\""))
                .andExpect(header().longValue("Content-Length", 3))
                .andExpect(content().bytes(bytes));
    }

    @Test
    void getFile_fallsBackToFileNameTypeWhenContentTypeMissing() throws Exception {
        when(service.downloadFile(eq("clip.mp3"))).thenReturn(
                new AudioFileService.DownloadedFile(new ByteArrayInputStream(new byte[]{1}), null, 1, "clip.mp3"));

        mvc.perform(get("/api/files/clip.mp3"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"));
    }

    @Test
    void getFile_usesOctetStreamForUnknownExtension() throws Exception {
        when(service.downloadFile(any())).thenReturn(
                new AudioFileService.DownloadedFile(new ByteArrayInputStream(new byte[]{1}), null, 1, "blob.unknownext"));

        mvc.perform(get("/api/files/blob.unknownext"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
    }

    @Test
    void getJobStatus_returnsDto() throws Exception {
        AudioJobStatusDto dto = AudioJobStatusDto.builder()
                .jobId(42L).audioId(7L).stage("TRANSCRIPTION").status("COMPLETED")
                .retryCount(0).objectKey("transcripts/7.txt").traceId("t-1")
                .build();
        when(service.getJobStatus(42L)).thenReturn(dto);

        mvc.perform(get("/api/jobs/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(42))
                .andExpect(jsonPath("$.stage").value("TRANSCRIPTION"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.objectKey").value("transcripts/7.txt"));
    }

    @Test
    void getJobStatus_returns404WhenJobMissing() throws Exception {
        when(service.getJobStatus(99L)).thenThrow(new JobNotFoundException(99L));

        mvc.perform(get("/api/jobs/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getJobStatus_nonNumericIdReturns400() throws Exception {
        mvc.perform(get("/api/jobs/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTranscript_returnsDtoWhenPresent() throws Exception {
        when(service.getTranscript(7L)).thenReturn(
                Optional.of(new TranscriptDto(1L, 7L, 42L, "hello world", "en", 0.9)));

        mvc.perform(get("/api/audio/7/transcript"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcriptText").value("hello world"))
                .andExpect(jsonPath("$.language").value("en"))
                .andExpect(jsonPath("$.confidence").value(0.9));
    }

    @Test
    void getTranscript_returns404WhenAbsent() throws Exception {
        when(service.getTranscript(8L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/audio/8/transcript"))
                .andExpect(status().isNotFound());
    }
}
