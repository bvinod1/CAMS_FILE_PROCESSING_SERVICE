package com.cams.fileprocessing.features.upload;

import com.cams.fileprocessing.features.upload.models.FileRecord;
import org.springframework.cloud.gcp.data.spanner.repository.SpannerRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileRecordRepository extends SpannerRepository<FileRecord, String> {
}
