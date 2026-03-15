package com.cams.fileprocessing.infrastructure.local.jpa;

import jakarta.persistence.*;

/**
 * JPA entity for the {@code validation_column_rules} table (local profile only).
 */
@Entity
@Table(name = "validation_column_rules")
public class ValidationColumnRuleJpaEntity {

    @Id
    @Column(name = "rule_id", length = 36)
    private String ruleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ValidationTemplateJpaEntity template;

    @Column(name = "column_name", nullable = false, length = 256)
    private String columnName;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "data_type", nullable = false, length = 20)
    private String dataType;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "allow_null", nullable = false)
    private boolean allowNull;

    @Column(name = "max_length")
    private Integer maxLength;

    @Column(name = "pattern", columnDefinition = "TEXT")
    private String pattern;

    public ValidationColumnRuleJpaEntity() {}

    // ---------- getters / setters ----------

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public ValidationTemplateJpaEntity getTemplate() { return template; }
    public void setTemplate(ValidationTemplateJpaEntity template) { this.template = template; }

    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public boolean isAllowNull() { return allowNull; }
    public void setAllowNull(boolean allowNull) { this.allowNull = allowNull; }

    public Integer getMaxLength() { return maxLength; }
    public void setMaxLength(Integer maxLength) { this.maxLength = maxLength; }

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }
}
