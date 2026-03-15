package com.cams.fileprocessing.features.upload;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

// TODO (migration backlog): Rewrite using Testcontainers per constitution §12.1.
// Mockito is used here as a migration holdover only — do not add new Mockito usage.
@WebFluxTest(UploadController.class)
class UploadControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private UploadService uploadService;

    @Test
    void requestUploadUrl_shouldReturnUploadResponse() throws Exception {
        // Given
        String fileName = "test.csv";
        String flowType = "NAV";
        String checksum = "d41d8cd98f00b204e9800998ecf8427e"; // valid MD5 hex
        UploadRequest uploadRequest = new UploadRequest(fileName, flowType, checksum);

        String fileId = UUID.randomUUID().toString();
        URL uploadUrl = new URL("http://localhost/upload");
        UploadResponse uploadResponse = new UploadResponse(fileId, uploadUrl.toString());

        when(uploadService.createUploadUrl(any(UploadRequest.class)))
                .thenReturn(Mono.just(uploadResponse));

        // When & Then
        webTestClient.post().uri("/api/v1/uploads")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(uploadRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.fileId").isEqualTo(fileId)
                .jsonPath("$.uploadUrl").isEqualTo(uploadUrl.toString());
    }

    @Test
    void requestUploadUrl_shouldAcceptSha256Checksum() throws Exception {
        // SHA-256 checksum (64 hex chars) must also be accepted
        String checksum64 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        UploadRequest request = new UploadRequest("data.csv", "NAV", checksum64);

        String fileId = UUID.randomUUID().toString();
        UploadResponse response = new UploadResponse(fileId, "http://localhost/upload");
        when(uploadService.createUploadUrl(any(UploadRequest.class)))
                .thenReturn(Mono.just(response));

        webTestClient.post().uri("/api/v1/uploads")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void confirmUpload_shouldReturnConfirmUploadResponse() {
        // Given
        String fileId = UUID.randomUUID().toString();
        ConfirmUploadResponse confirmResponse =
                new ConfirmUploadResponse(fileId, "UPLOADED", Instant.now());

        when(uploadService.confirmUpload(eq(fileId)))
                .thenReturn(Mono.just(confirmResponse));

        // When & Then
        webTestClient.post().uri("/api/v1/uploads/" + fileId + "/confirm")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.fileId").isEqualTo(fileId)
                .jsonPath("$.status").isEqualTo("UPLOADED")
                .jsonPath("$.confirmedAt").isNotEmpty();
    }
}
