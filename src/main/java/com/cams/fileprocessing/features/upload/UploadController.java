package com.cams.fileprocessing.features.upload;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST controller exposing the secure file upload initiation endpoint.
 * Clients call {@code POST /api/v1/uploads} to receive a pre-signed GCS URL.
 */
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    /**
     * Generates a pre-signed GCS upload URL for the given file metadata.
     *
     * @param request validated upload request from the client
     * @return a {@link Mono} emitting an {@link UploadResponse} containing the fileId and signed URL
     */
    @PostMapping
    public Mono<UploadResponse> requestUploadUrl(@Valid @RequestBody UploadRequest request) {
        log.info("Received upload URL request: fileName={}, flowType={}", request.fileName(), request.flowType());
        return uploadService.createUploadUrl(request);
    }
}
