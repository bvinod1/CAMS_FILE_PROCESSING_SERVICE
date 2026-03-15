package com.cams.fileprocessing.infrastructure.local.jpa;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity for the {@code validation_templates} table (local profile only).
 */
@Entity
@Table(name = "validation_templates")
public class ValidationTemplateJpaEntity {

    @Id
    @Column(name = "template_id", length = 36)
    private String templateId;

    @Column(name = "flow_type", nullable = false, length = 64)
    private String flowType;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<ValidationColumnRuleJpaEntity> columnRules = new ArrayList<>();

    public ValidationTemplateJpaEntity() {}

    // ---------- getters / setters ----------

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public String getFlowType() { return flowType; }
    public void setFlowType(String flowType) { this.flowType = flowType; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<ValidationColumnRuleJpaEntity> getColumnRules() { return columnRules; }
    public void setColumnRules(List<ValidationColumnRuleJpaEntity> columnRules) {
        this.columnRules = columnRules;
    }
}
