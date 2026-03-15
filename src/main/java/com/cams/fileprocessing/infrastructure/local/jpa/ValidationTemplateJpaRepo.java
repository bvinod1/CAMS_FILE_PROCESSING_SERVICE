package com.cams.fileprocessing.infrastructure.local.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link ValidationTemplateJpaEntity}.
 * Internal to the local infrastructure layer — not exposed to business code.
 */
public interface ValidationTemplateJpaRepo
        extends JpaRepository<ValidationTemplateJpaEntity, String> {

    Optional<ValidationTemplateJpaEntity> findByFlowTypeAndActiveTrue(String flowType);

    List<ValidationTemplateJpaEntity> findByFlowTypeOrderByVersionDesc(String flowType);

    @Query("SELECT DISTINCT e.flowType FROM ValidationTemplateJpaEntity e WHERE e.active = true")
    List<String> findActiveFlowTypes();

    @Query("SELECT COALESCE(MAX(e.version), 0) FROM ValidationTemplateJpaEntity e WHERE e.flowType = :flowType")
    int findMaxVersionByFlowType(@Param("flowType") String flowType);
}
