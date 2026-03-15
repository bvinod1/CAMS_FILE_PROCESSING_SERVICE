package com.cams.fileprocessing.interfaces;

import java.net.URL;

/**
 * Port interface for generating pre-signed object storage PUT URLs.
 *
 * <p>Clients use the returned URL to upload a file directly to object storage
 * without the bytes passing through this service. This keeps the service
 * stateless with respect to file content.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@code GcsSignedUrlAdapter} — GCS V4 signed URL ({@code @Profile("gcp")})</li>
 *   <li>{@code LocalSignedUrlAdapter} — MinIO pre-signed URL ({@code @Profile("local")})</li>
 * </ul>
 */
public interface SignedUrlPort {

    /**
     * Generates a time-limited pre-signed HTTP PUT URL for the given object path.
     *
     * <p>The URL must:
     * <ul>
     *   <li>Expire in exactly 15 minutes (per constitution §11)</li>
     *   <li>Be scoped to the quarantine bucket only</li>
     *   <li>Only permit HTTP PUT</li>
     * </ul>
     *
     * @param objectName the full object path within the quarantine bucket
     *                   (e.g. {@code "uploads/2026/03/15/<fileId>/data.csv"})
     * @return the pre-signed {@link URL}; never null
     * @throws RuntimeException if the URL cannot be generated
     */
    URL generateSignedPutUrl(String objectName);
}
