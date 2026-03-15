package com.cams.fileprocessing.features.upload;

import com.cams.fileprocessing.features.upload.models.FileRecord;
import com.cams.fileprocessing.interfaces.MessagePublisherPort;
import com.cams.fileprocessing.interfaces.SignedUrlPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URL;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// TODO (migration backlog): Rewrite using Testcontainers per constitution §12.1.
// Mockito is used here as a migration holdover only — do not add new Mockito usage.
@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @Mock
    private FileRecordRepository fileRecordRepository;

    @Mock
    private SignedUrlPort signedUrlPort;

    @Mock
    private MessagePublisherPort messagePublisher;

    @InjectMocks
    private UploadService uploadService;

    @Test
    void createUploadUrl_shouldSaveRecordAndReturnResponse() throws Exception {
        // Given
        String fileName = "test.csv";
        String flowType = "NAV";
        String checksum = "d41d8cd98f00b204e9800998ecf8427e";
        UploadRequest uploadRequest = new UploadRequest(fileName, flowType, checksum);

        FileRecord savedRecord = new FileRecord();
        savedRecord.setFileId(UUID.randomUUID().toString());
        savedRecord.setOriginalFileName(fileName);
        savedRecord.setFlowType(flowType);
        savedRecord.setChecksum(checksum);
        savedRecord.setStatus("AWAITING_UPLOAD");

        URL presignedUrl = new URL("http://localhost/upload");

        when(fileRecordRepository.save(any(FileRecord.class))).thenReturn(savedRecord);
        when(signedUrlPort.generateSignedPutUrl(anyString())).thenReturn(presignedUrl);

        // When
        Mono<UploadResponse> result = uploadService.createUploadUrl(uploadRequest);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(response ->
                        response.fileId() != null &&
                        response.uploadUrl().equals(presignedUrl.toString()))
                .verifyComplete();
    }

    @Test
    void confirmUpload_shouldTransitionToUploadedAndPublishEvent() {
        // Given
        String fileId = UUID.randomUUID().toString();

        FileRecord existingRecord = new FileRecord();
        existingRecord.setFileId(fileId);
        existingRecord.setOriginalFileName("test.csv");
        existingRecord.setFlowType("NAV");
        existingRecord.setChecksum("d41d8cd98f00b204e9800998ecf8427e");
        existingRecord.setStatus("AWAITING_UPLOAD");
        existingRecord.setIngressChannel("REST");

        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(existingRecord));
        when(fileRecordRepository.save(any(FileRecord.class))).thenReturn(existingRecord);

        // When
        Mono<ConfirmUploadResponse> result = uploadService.confirmUpload(fileId);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(response ->
                        response.fileId().equals(fileId) &&
                        response.status().equals("UPLOADED") &&
                        response.confirmedAt() != null)
                .verifyComplete();

        verify(messagePublisher).publish(
                eq(UploadService.TOPIC_FILE_RECEIVED), any());
    }
}
