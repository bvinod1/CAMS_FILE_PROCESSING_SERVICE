package com.cams.fileprocessing.features.upload.models;

import com.google.cloud.Timestamp;
import org.springframework.cloud.gcp.data.spanner.core.mapping.Column;
import org.springframework.cloud.gcp.data.spanner.core.mapping.PrimaryKey;
import org.springframework.cloud.gcp.data.spanner.core.mapping.Table;

@Table(name = "file_records")
public class FileRecord {

    @PrimaryKey
    @Column(name = "fileId")
    private String fileId;

    @Column(name = "originalFileName")
    private String originalFileName;

    @Column(name = "flowType")
    private String flowType;

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "status")
    private String status;

    @Column(name = "createdAt")
    private Timestamp createdAt;

    @Column(name = "updatedAt")
    private Timestamp updatedAt;

    @Column(name = "gcsBucket")
    private String gcsBucket;

    @Column(name = "gcsObjectPath")
    private String gcsObjectPath;

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getFlowType() {
        return flowType;
    }

    public void setFlowType(String flowType) {
        this.flowType = flowType;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getGcsBucket() {
        return gcsBucket;
    }

    public void setGcsBucket(String gcsBucket) {
        this.gcsBucket = gcsBucket;
    }

    public String getGcsObjectPath() {
        return gcsObjectPath;
    }

    public void setGcsObjectPath(String gcsObjectPath) {
        this.gcsObjectPath = gcsObjectPath;
    }
}
