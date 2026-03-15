package com.cams.fileprocessing.features.upload;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST controller exposing the file upload initiation and confirmation endpoints.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /api/v1/uploads} — request a pre-signed upload URL</li>
 *   <li>{@code POST /api/v1/uploads/{fileId}/confirm} — confirm the upload is complete
 *       and trigger the scanning pipeline</li>
 * </ul>
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
     * Generates a pre-signed object-storage PUT URL for the given file metadata.
     * The client must upload the file bytes directly to this URL within 15 minutes.
     *
     * @param request validated upload request from the client
     * @return a {@link Mono} emitting an {@link UploadResponse} containing the fileId and signed URL
     */
    @PostMapping
    public Mono<UploadResponse> requestUploadUrl(@Valid @RequestBody UploadRequest request) {
        log.info("Received upload URL request: fileName={}, flowType={}",
                request.fileName(), request.flowType());
        return uploadService.createUploadUrl(request);
    }

    /**
     * Confirms that the client has finished uploading the file to the quarantine bucket.
     *
     * <p>This transitions the file record to {@code UPLOADED} status and publishes a
     * {@code FileReceivedEvent} to trigger the malware scanning pipeline (Epic 2).
     *
     * @param fileId the unique file identifier returned by {@code POST /api/v1/uploads}
     * @return a {@link Mono} emitting a {@link ConfirmUploadResponse} with the new status
     */
    @PostMapping("/{fileId}/confirm")
    @ResponseStatus(HttpStatus.OK)
    public Mono<ConfirmUploadResponse> confirmUpload(@PathVariable String fileId) {
        log.info("Received upload confirmation: fileId={}", fileId);
        return uploadService.confirmUpload(fileId);
    }
}
