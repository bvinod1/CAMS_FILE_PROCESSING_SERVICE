package com.cams.fileprocessing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class that exposes GCP project-level properties to the application context.
 */
@Configuration
public class GcpConfig {

    @Value("${spring.cloud.gcp.project-id}")
    private String projectId;

    public String getProjectId() {
        return projectId;
    }
}
