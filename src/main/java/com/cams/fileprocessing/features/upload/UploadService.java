package com.cams.fileprocessing.features.upload;

import com.cams.fileprocessing.features.upload.models.FileRecord;
import com.cams.fileprocessing.gcp.GcsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.util.UUID;

/**
 * Service that orchestrates the creation of a {@link FileRecord} in Spanner and
 * generates a pre-signed GCS URL for the client to upload a file directly.
 */
@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    private final FileRecordRepository fileRecordRepository;
    private final GcsService gcsService;

    public UploadService(FileRecordRepository fileRecordRepository, GcsService gcsService) {
        this.fileRecordRepository = fileRecordRepository;
        this.gcsService = gcsService;
    }

    /**
     * Creates a pre-signed upload URL for the given request and persists a
     * {@link FileRecord} with status {@code AWAITING_UPLOAD}.
     *
     * @param request the upload request containing file metadata
     * @return a {@link Mono} emitting the {@link UploadResponse} with the signed URL
     */
    public Mono<UploadResponse> createUploadUrl(UploadRequest request) {
        return Mono.fromCallable(() -> {
            String fileId = UUID.randomUUID().toString();
            String objectName = fileId + "/" + request.fileName();

            log.info("Creating upload URL request: fileId={}, fileName={}, flowType={}",
                    fileId, request.fileName(), request.flowType());

            FileRecord record = new FileRecord();
            record.setFileId(fileId);
            record.setOriginalFileName(request.fileName());
            record.setFlowType(request.flowType());
            record.setChecksum(request.checksum());
            record.setStatus("AWAITING_UPLOAD");

            fileRecordRepository.save(record);
            log.debug("FileRecord persisted: fileId={}, status=AWAITING_UPLOAD", fileId);

            URL signedUrl = gcsService.generateV4PutObjectSignedUrl(objectName);
            log.info("Pre-signed URL generated successfully: fileId={}", fileId);

            return new UploadResponse(fileId, signedUrl.toString());
        });
    }
}
