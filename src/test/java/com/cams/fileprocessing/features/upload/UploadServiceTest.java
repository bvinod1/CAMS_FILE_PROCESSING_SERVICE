package com.cams.fileprocessing.features.upload;

import com.cams.fileprocessing.features.upload.models.FileRecord;
import com.cams.fileprocessing.gcp.GcsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URL;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @Mock
    private FileRecordRepository fileRecordRepository;

    @Mock
    private GcsService gcsService;

    @InjectMocks
    private UploadService uploadService;

    @Test
    void createUploadUrl_shouldSaveRecordAndReturnResponse() throws Exception {
        // Given
        String fileName = "test.csv";
        String flowType = "NAV";
        String checksum = "checksum";
        UploadRequest uploadRequest = new UploadRequest(fileName, flowType, checksum);

        FileRecord savedRecord = new FileRecord();
        savedRecord.setFileId(UUID.randomUUID().toString());
        savedRecord.setOriginalFileName(fileName);
        savedRecord.setFlowType(flowType);
        savedRecord.setChecksum(checksum);
        savedRecord.setStatus("AWAITING_UPLOAD");

        URL presignedUrl = new URL("http://localhost/upload");

        when(fileRecordRepository.save(any(FileRecord.class))).thenReturn(savedRecord);
        when(gcsService.generateV4PutObjectSignedUrl(anyString())).thenReturn(presignedUrl);

        // When
        Mono<UploadResponse> result = uploadService.createUploadUrl(uploadRequest);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(response ->
                        response.fileId() != null &&
                        response.uploadUrl().equals(presignedUrl.toString()))
                .verifyComplete();
    }
}
