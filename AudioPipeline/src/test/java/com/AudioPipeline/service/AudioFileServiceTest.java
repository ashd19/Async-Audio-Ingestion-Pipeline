package com.AudioPipeline.service;

import com.AudioPipeline.Configuration.MinioConfig;
import com.AudioPipeline.dto.AudioFileDto;
import com.AudioPipeline.dto.AudioJobStatusDto;
import com.AudioPipeline.dto.TranscriptDto;
import com.AudioPipeline.entity.AudioFilesEntity;
import com.AudioPipeline.entity.AudioProcessingJobEntity;
import com.AudioPipeline.entity.TranscriptEntity;
import com.AudioPipeline.exception.JobNotFoundException;
import com.AudioPipeline.repository.AudioFileRepository;
import com.AudioPipeline.repository.AudioProcessingJobRepository;
import com.AudioPipeline.repository.TranscriptRepository;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudioFileServiceTest {

    private static final String BUCKET = "audio-test";

    @Mock AudioFileRepository audioFileRepository;
    @Mock AudioProcessingJobRepository jobRepository;
    @Mock TranscriptRepository transcriptRepository;
    @Mock AudioEventPublisher eventPublisher;
    @Mock MinioClient minioClient;

    AudioFileService service;

    @BeforeEach
    void setUp() {
        MinioConfig config = new MinioConfig();
        config.setBucket(BUCKET);
        service = new AudioFileService(audioFileRepository, jobRepository, transcriptRepository,
                eventPublisher, minioClient, config);
    }

    private void stubPersistence() {
        when(audioFileRepository.save(any(AudioFilesEntity.class))).thenAnswer(inv -> {
            AudioFilesEntity e = inv.getArgument(0);
            e.setId(7L);
            return e;
        });
        when(jobRepository.save(any(AudioProcessingJobEntity.class))).thenAnswer(inv -> {
            AudioProcessingJobEntity j = inv.getArgument(0);
            j.setId(42L);
            return j;
        });
    }

    @Test
    void uploadFile_storesObjectPersistsRowsAndPublishesEvent() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        stubPersistence();
        MockMultipartFile file = new MockMultipartFile("file", "my song (1).mp3", "audio/mpeg", new byte[]{1, 2, 3});

        AudioFileDto dto = service.uploadFile(file, "10.0.0.1");

        ArgumentCaptor<PutObjectArgs> put = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(put.capture());
        assertThat(put.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(put.getValue().object()).endsWith("-my_song__1_.mp3");
        assertThat(put.getValue().contentType()).isEqualTo("audio/mpeg");

        ArgumentCaptor<AudioFilesEntity> fileRow = ArgumentCaptor.forClass(AudioFilesEntity.class);
        verify(audioFileRepository).save(fileRow.capture());
        assertThat(fileRow.getValue().getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(fileRow.getValue().getFilePath()).isEqualTo(put.getValue().object());

        ArgumentCaptor<AudioProcessingJobEntity> jobRow = ArgumentCaptor.forClass(AudioProcessingJobEntity.class);
        verify(jobRepository).save(jobRow.capture());
        assertThat(jobRow.getValue().getStage()).isEqualTo("NORMALIZATION");
        assertThat(jobRow.getValue().getStatus()).isEqualTo("QUEUED");
        assertThat(jobRow.getValue().getRetryCount()).isZero();
        assertThat(jobRow.getValue().getAudioFileId()).isEqualTo(7L);

        verify(eventPublisher).publishUploadedEvent(7L, 42L, put.getValue().object(), dto.getTraceId());
        assertThat(dto.getAudioId()).isEqualTo(7L);
        assertThat(dto.getJobId()).isEqualTo(42L);
        assertThat(dto.getStatusUrl()).isEqualTo("/api/jobs/42");
        assertThat(dto.getTraceId()).isEqualTo(jobRow.getValue().getTraceId());
    }

    @Test
    void uploadFile_createsBucketWhenMissing() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        stubPersistence();

        service.uploadFile(new MockMultipartFile("file", "a.mp3", "audio/mpeg", new byte[]{1}), "ip");

        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void uploadFile_usesFallbackNameWhenOriginalFilenameNull() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        stubPersistence();
        MockMultipartFile file = new MockMultipartFile("file", null, "audio/mpeg", new byte[]{1}) {
            @Override
            public String getOriginalFilename() {
                return null;
            }
        };

        service.uploadFile(file, "ip");

        ArgumentCaptor<PutObjectArgs> put = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(put.capture());
        assertThat(put.getValue().object()).endsWith("-file");
    }

    @Test
    void uploadFile_rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.mp3", "audio/mpeg", new byte[0]);

        assertThatThrownBy(() -> service.uploadFile(empty, "ip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("File is required");
        assertThatThrownBy(() -> service.uploadFile(null, "ip"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(minioClient, audioFileRepository, jobRepository, eventPublisher);
    }

    @Test
    void uploadFile_doesNotPersistOrPublishWhenStorageFails() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenThrow(new RuntimeException("minio down"));

        assertThatThrownBy(() ->
                service.uploadFile(new MockMultipartFile("file", "a.mp3", "audio/mpeg", new byte[]{1}), "ip"))
                .hasMessage("minio down");

        verify(audioFileRepository, never()).save(any());
        verify(jobRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void downloadFile_returnsStreamMetadataAndStripsPathFromFileName() throws Exception {
        StatObjectResponse stat = org.mockito.Mockito.mock(StatObjectResponse.class);
        when(stat.contentType()).thenReturn("audio/mpeg");
        when(stat.size()).thenReturn(123L);
        GetObjectResponse stream = org.mockito.Mockito.mock(GetObjectResponse.class);
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(stat);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(stream);

        AudioFileService.DownloadedFile result = service.downloadFile("normalized/7.mp3");

        assertThat(result.fileName()).isEqualTo("7.mp3");
        assertThat(result.contentType()).isEqualTo("audio/mpeg");
        assertThat(result.size()).isEqualTo(123L);
        assertThat(result.stream()).isSameAs(stream);
    }

    @Test
    void downloadFile_rejectsBlankKey() {
        assertThatThrownBy(() -> service.downloadFile(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Object key is required");
        assertThatThrownBy(() -> service.downloadFile(null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(minioClient);
    }

    @Test
    void getJobStatus_mapsEntityToDto() {
        AudioProcessingJobEntity job = new AudioProcessingJobEntity();
        job.setId(42L);
        job.setAudioFileId(7L);
        job.setStage("TRANSCRIPTION");
        job.setStatus("FAILED");
        job.setRetryCount(2);
        job.setObjectKey("normalized/7.mp3");
        job.setTraceId("trace-1");
        job.setErrorCode("DOWNLOAD_FAILED");
        job.setErrorMessage("boom");
        when(jobRepository.findById(42L)).thenReturn(Optional.of(job));

        AudioJobStatusDto dto = service.getJobStatus(42L);

        assertThat(dto.jobId()).isEqualTo(42L);
        assertThat(dto.audioId()).isEqualTo(7L);
        assertThat(dto.stage()).isEqualTo("TRANSCRIPTION");
        assertThat(dto.status()).isEqualTo("FAILED");
        assertThat(dto.retryCount()).isEqualTo(2);
        assertThat(dto.objectKey()).isEqualTo("normalized/7.mp3");
        assertThat(dto.traceId()).isEqualTo("trace-1");
        assertThat(dto.errorCode()).isEqualTo("DOWNLOAD_FAILED");
        assertThat(dto.errorMessage()).isEqualTo("boom");
    }

    @Test
    void getJobStatus_throwsWhenMissing() {
        when(jobRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJobStatus(99L))
                .isInstanceOf(JobNotFoundException.class)
                .hasMessage("Job not found: 99");
    }

    @Test
    void getTranscript_mapsEntityToDto() {
        TranscriptEntity entity = new TranscriptEntity();
        entity.setId(1L);
        entity.setAudioFileId(7L);
        entity.setJobId(42L);
        entity.setTranscriptText("hello world");
        entity.setLanguage("en");
        entity.setConfidence(0.93);
        when(transcriptRepository.findByAudioFileId(7L)).thenReturn(Optional.of(entity));

        Optional<TranscriptDto> result = service.getTranscript(7L);

        assertThat(result).contains(new TranscriptDto(1L, 7L, 42L, "hello world", "en", 0.93));
    }

    @Test
    void getTranscript_emptyWhenNoneStored() {
        when(transcriptRepository.findByAudioFileId(eq(8L))).thenReturn(Optional.empty());

        assertThat(service.getTranscript(8L)).isEmpty();
    }
}
