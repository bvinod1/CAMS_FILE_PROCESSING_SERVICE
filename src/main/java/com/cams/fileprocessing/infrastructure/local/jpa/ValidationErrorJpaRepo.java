package com.cams.fileprocessing.infrastructure.local.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data JPA repository for validation errors (local profile only). */
public interface ValidationErrorJpaRepo
        extends JpaRepository<ValidationErrorJpaEntity, String> {

    List<ValidationErrorJpaEntity> findByResultId(String resultId);
}
