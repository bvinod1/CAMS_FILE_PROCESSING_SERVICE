package com.cams.fileprocessing.features.upload;

import com.cams.fileprocessing.common.FileStatus;
import com.cams.fileprocessing.features.scan.FileReceivedEvent;
import com.cams.fileprocessing.features.upload.models.FileRecord;
import com.cams.fileprocessing.interfaces.MessagePublisherPort;
import com.cams.fileprocessing.interfaces.SignedUrlPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.time.Instant;
import java.util.UUID;

/**
 * Service that orchestrates pre-signed URL generation and upload confirmation.
 *
 * <p>This service is the entry point for the upload feature's business logic.
 * All infrastructure access is via port interfaces:
 * <ul>
 *   <li>{@link SignedUrlPort} — generates the pre-signed upload URL</li>
 *   <li>{@link MessagePublisherPort} — publishes {@link FileReceivedEvent} on confirmation</li>
 * </ul>
 *
 * <p>No cloud-vendor SDK classes are imported here. The concrete adapters are
 * injected by Spring based on the active profile.
 */
@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    static final String TOPIC_FILE_RECEIVED = "cams.file.received";

    private final FileRecordRepository  fileRecordRepository;
    private final SignedUrlPort         signedUrlPort;
    private final MessagePublisherPort  messagePublisher;

    /**
     * @param fileRecordRepository persistence port for {@link FileRecord}
     * @param signedUrlPort        signed URL adapter (GCS, MinIO, etc.)
     * @param messagePublisher     event publishing adapter
     */
    public UploadService(
            FileRecordRepository fileRecordRepository,
            SignedUrlPort        signedUrlPort,
            MessagePublisherPort messagePublisher) {
        this.fileRecordRepository = fileRecordRepository;
        this.signedUrlPort        = signedUrlPort;
        this.messagePublisher     = messagePublisher;
    }

    /**
     * Creates a pre-signed upload URL and persists a {@link FileRecord} with
     * status {@link FileStatus#AWAITING_UPLOAD}.
     *
     * @param request the upload request containing file metadata
     * @return a {@link Mono} emitting the {@link UploadResponse} with the signed URL
     */
    public Mono<UploadResponse> createUploadUrl(UploadRequest request) {
        return Mono.fromCallable(() -> {
            String fileId     = UUID.randomUUID().toString();
            String objectName = fileId + "/" + request.fileName();

            log.info("Creating upload URL: fileId={}, fileName={}, flowType={}",
                    fileId, request.fileName(), request.flowType());

            FileRecord record = new FileRecord();
            record.setFileId(fileId);
            record.setOriginalFileName(request.fileName());
            record.setFlowType(request.flowType());
            record.setChecksum(request.checksum());
            record.setStatus(FileStatus.AWAITING_UPLOAD.name());
            record.setIngressChannel("REST");
            record.setPriority(1);

            fileRecordRepository.save(record);
            log.debug("FileRecord persisted: fileId={}, status={}",
                    fileId, FileStatus.AWAITING_UPLOAD);

            URL signedUrl = signedUrlPort.generateSignedPutUrl(objectName);
            log.info("Pre-signed URL generated: fileId={}", fileId);

            return new UploadResponse(fileId, signedUrl.toString());
        });
    }

    /**
     * Confirms that the client has finished uploading the file to the quarantine bucket.
     *
     * <p>Transitions the record status from {@link FileStatus#AWAITING_UPLOAD} to
     * {@link FileStatus#UPLOADED} and publishes a {@link FileReceivedEvent} to the
     * message queue so the Scan Worker can pick it up.
     *
     * @param fileId the unique identifier returned by {@link #createUploadUrl}
     * @return a {@link Mono} emitting a {@link ConfirmUploadResponse} with the new status
     * @throws IllegalStateException if no record is found for {@code fileId}
     */
    public Mono<ConfirmUploadResponse> confirmUpload(String fileId) {
        return Mono.fromCallable(() -> {
            log.info("Confirming upload: fileId={}", fileId);

            FileRecord record = fileRecordRepository.findById(fileId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No file record found for fileId=" + fileId));

            if (!FileStatus.AWAITING_UPLOAD.name().equals(record.getStatus())) {
                log.warn("confirmUpload called but record is not in AWAITING_UPLOAD state "
                        + "— fileId={}, currentStatus={}", fileId, record.getStatus());
            }

            record.setStatus(FileStatus.UPLOADED.name());
            fileRecordRepository.save(record);
            log.debug("FileRecord status updated: fileId={}, status={}", fileId, FileStatus.UPLOADED);

            FileReceivedEvent event = new FileReceivedEvent(
                    UUID.randomUUID().toString(),
                    fileId,
                    record.getOriginalFileName(),
                    record.getFlowType(),
                    record.getIngressChannel() != null ? record.getIngressChannel() : "REST",
                    record.getChecksum(),
                    record.getGcsBucket(),
                    record.getGcsObjectPath(),
                    record.getPriority(),
                    Instant.now()
            );
            messagePublisher.publish(TOPIC_FILE_RECEIVED, event);
            log.info("FileReceivedEvent published: fileId={}", fileId);

            return new ConfirmUploadResponse(fileId, FileStatus.UPLOADED.name(), Instant.now());
        });
    }
}
