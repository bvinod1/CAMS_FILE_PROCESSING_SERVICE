package com.cams.fileprocessing.infrastructure.local.jpa;

import jakarta.persistence.*;

/**
 * JPA entity for the {@code validation_errors} table (local profile only).
 * INSERT-only — never updated or deleted (enforced at DB level too).
 */
@Entity
@Table(name = "validation_errors")
public class ValidationErrorJpaEntity {

    @Id
    @Column(name = "error_id", length = 36)
    private String errorId;

    @Column(name = "result_id", nullable = false, length = 36)
    private String resultId;

    @Column(name = "column_name", length = 256)
    private String columnName;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "error_code", nullable = false, length = 30)
    private String errorCode;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    public ValidationErrorJpaEntity() {}

    // ---------- getters / setters ----------

    public String getErrorId() { return errorId; }
    public void setErrorId(String errorId) { this.errorId = errorId; }

    public String getResultId() { return resultId; }
    public void setResultId(String resultId) { this.resultId = resultId; }

    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
