package com.cams.fileprocessing.infrastructure.local.jpa;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity for the {@code validation_results} table (local profile only).
 * INSERT-only — never updated after creation.
 */
@Entity
@Table(name = "validation_results")
public class ValidationResultJpaEntity {

    @Id
    @Column(name = "result_id", length = 36)
    private String resultId;

    @Column(name = "file_id", nullable = false, length = 36)
    private String fileId;

    @Column(name = "flow_type", nullable = false, length = 64)
    private String flowType;

    @Column(name = "template_id", nullable = false, length = 36)
    private String templateId;

    @Column(name = "template_version", nullable = false)
    private int templateVersion;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "validated_at", nullable = false)
    private Instant validatedAt;

    public ValidationResultJpaEntity() {}

    // ---------- getters / setters ----------

    public String getResultId() { return resultId; }
    public void setResultId(String resultId) { this.resultId = resultId; }

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public String getFlowType() { return flowType; }
    public void setFlowType(String flowType) { this.flowType = flowType; }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public int getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(int templateVersion) { this.templateVersion = templateVersion; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getErrorCount() { return errorCount; }
    public void setErrorCount(int errorCount) { this.errorCount = errorCount; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public Instant getValidatedAt() { return validatedAt; }
    public void setValidatedAt(Instant validatedAt) { this.validatedAt = validatedAt; }
}
