package com.cams.fileprocessing.infrastructure.local.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data JPA repository for validation results (local profile only). */
public interface ValidationResultJpaRepo
        extends JpaRepository<ValidationResultJpaEntity, String> {

    List<ValidationResultJpaEntity> findByFileId(String fileId);
}
