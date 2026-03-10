package com.cams.fileprocessing.features.upload;

import com.cams.fileprocessing.features.upload.models.FileRecord;
import com.cams.fileprocessing.gcp.GcsService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.util.UUID;

@Service
public class UploadService {

    private final FileRecordRepository fileRecordRepository;
    private final GcsService gcsService;

    public UploadService(FileRecordRepository fileRecordRepository, GcsService gcsService) {
        this.fileRecordRepository = fileRecordRepository;
        this.gcsService = gcsService;
    }

    public Mono<UploadResponse> createUploadUrl(UploadRequest request) {
        return Mono.fromCallable(() -> {
            String fileId = UUID.randomUUID().toString();
            String objectName = fileId + "/" + request.fileName();

            FileRecord record = new FileRecord();
            record.setFileId(fileId);
            record.setOriginalFileName(request.fileName());
            record.setFlowType(request.flowType());
            record.setChecksum(request.checksum());
            record.setStatus("AWAITING_UPLOAD");
            // createdAt and updatedAt are set by Spanner
            
            fileRecordRepository.save(record);

            URL signedUrl = gcsService.generateV4PutObjectSignedUrl(objectName);

            return new UploadResponse(fileId, signedUrl.toString());
        });
    }
}
