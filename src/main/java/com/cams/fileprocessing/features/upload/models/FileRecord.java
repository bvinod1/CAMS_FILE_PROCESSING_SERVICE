package com.cams.fileprocessing.features.upload.models;

import com.cams.fileprocessing.common.FileStatus;
import com.google.cloud.Timestamp;
import com.google.cloud.spring.data.spanner.core.mapping.Column;
import com.google.cloud.spring.data.spanner.core.mapping.PrimaryKey;
import com.google.cloud.spring.data.spanner.core.mapping.Table;

/**
 * Spanner entity representing the lifecycle record of a single uploaded file.
 *
 * <p>The record is created with status {@link FileStatus#AWAITING_UPLOAD} when a pre-signed URL
 * is issued, and transitions through states as the file is processed by each pipeline stage.
 *
 * <p>Status values are constrained to {@link FileStatus} — always use {@code FileStatus.name()}
 * when setting the status field; never use raw string literals.
 */
@Table(name = "file_records")
public class FileRecord {

    @PrimaryKey
    @Column(name = "fileId")
    private String fileId;

    @Column(name = "originalFileName")
    private String originalFileName;

    @Column(name = "flowType")
    private String flowType;

    /** Client-supplied MD5 or SHA-256 checksum for integrity verification. */
    @Column(name = "checksum")
    private String checksum;

    /**
     * System-computed SHA-256 checksum after the file lands in the quarantine bucket.
     * Populated by the scan worker before scanning begins.
     */
    @Column(name = "checksumSha256")
    private String checksumSha256;

    /**
     * The ingress channel through which the file entered the platform.
     * One of: {@code "REST"}, {@code "SFTP"}, {@code "GCS_TRIGGER"}.
     * Defaults to {@code "REST"} for uploads via the HTTP API.
     */
    @Column(name = "ingressChannel")
    private String ingressChannel;

    /**
     * Processing priority. Lower number = higher priority.
     * 0 = P0 (NAV files, never starved), 1 = standard, 2 = bulk.
     */
    @Column(name = "priority")
    private int priority;

    /**
     * Current lifecycle status. Must always be a valid {@link FileStatus} name.
     * Use {@code FileStatus.XYZ.name()} when setting this field.
     */
    @Column(name = "status")
    private String status;

    @Column(name = "createdAt")
    private Timestamp createdAt;

    @Column(name = "updatedAt")
    private Timestamp updatedAt;

    /** Timestamp when the ClamAV scan completed (null until scanning is done). */
    @Column(name = "scannedAt")
    private Timestamp scannedAt;

    /** Foreign key to the {@code scan_results} table; null until scan completes. */
    @Column(name = "scanId")
    private String scanId;

    @Column(name = "gcsBucket")
    private String gcsBucket;

    @Column(name = "gcsObjectPath")
    private String gcsObjectPath;

    // -------------------------------------------------------------------------
    // Getters & Setters
    // -------------------------------------------------------------------------

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }

    public String getFlowType() { return flowType; }
    public void setFlowType(String flowType) { this.flowType = flowType; }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    public String getChecksumSha256() { return checksumSha256; }
    public void setChecksumSha256(String checksumSha256) { this.checksumSha256 = checksumSha256; }

    public String getIngressChannel() { return ingressChannel; }
    public void setIngressChannel(String ingressChannel) { this.ingressChannel = ingressChannel; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public Timestamp getScannedAt() { return scannedAt; }
    public void setScannedAt(Timestamp scannedAt) { this.scannedAt = scannedAt; }

    public String getScanId() { return scanId; }
    public void setScanId(String scanId) { this.scanId = scanId; }

    public String getGcsBucket() { return gcsBucket; }
    public void setGcsBucket(String gcsBucket) { this.gcsBucket = gcsBucket; }

    public String getGcsObjectPath() { return gcsObjectPath; }
    public void setGcsObjectPath(String gcsObjectPath) { this.gcsObjectPath = gcsObjectPath; }
}
