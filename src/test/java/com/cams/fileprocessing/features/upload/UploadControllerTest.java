package com.cams.fileprocessing.features.upload;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
        String checksum = "checksum";
        UploadRequest uploadRequest = new UploadRequest(fileName, flowType, checksum);

        UUID fileId = UUID.randomUUID();
        URL uploadUrl = new URL("http://localhost/upload");
        UploadResponse uploadResponse = new UploadResponse(fileId, uploadUrl.toString());

        when(uploadService.createUploadUrl(any(UploadRequest.class))).thenReturn(Mono.just(uploadResponse));

        // When & Then
        webTestClient.post().uri("/api/v1/uploads")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(uploadRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.fileId").isEqualTo(fileId.toString())
                .jsonPath("$.uploadUrl").isEqualTo(uploadUrl.toString());
    }
}
