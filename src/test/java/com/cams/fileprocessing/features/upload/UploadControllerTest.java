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
        String checksum = "d41d8cd98f00b204e9800998ecf8427e"; // valid MD5 hex
        UploadRequest uploadRequest = new UploadRequest(fileName, flowType, checksum);

        String fileId = UUID.randomUUID().toString();
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
                .jsonPath("$.fileId").isEqualTo(fileId)
                .jsonPath("$.uploadUrl").isEqualTo(uploadUrl.toString());
    }
}
