package com.cams.fileprocessing.features.upload;

import com.cams.fileprocessing.features.upload.models.FileRecord;
import com.google.cloud.spring.data.spanner.repository.SpannerRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link FileRecord} entities persisted in Google Cloud Spanner.
 */
@Repository
public interface FileRecordRepository extends SpannerRepository<FileRecord, String> {
}
