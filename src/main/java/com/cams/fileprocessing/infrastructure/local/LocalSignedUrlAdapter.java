package com.cams.fileprocessing.infrastructure.local;

import com.cams.fileprocessing.interfaces.SignedUrlPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;

/**
 * Local adapter for {@link SignedUrlPort}.
 *
 * <p>Generates a MinIO-compatible pre-signed PUT URL for local development.
 * The URL points to the local MinIO instance (default: {@code http://localhost:9000}).
 *
 * <p>Active when Spring profile {@code local} is set.
 *
 * <p><b>TODO T207-E</b>: Replace this stub with a proper MinIO pre-signed URL
 * generated via the AWS SDK v2 S3 pre-signer (MinIO is fully S3-compatible).
 * Until then, the URL is a plain http:// URL that MinIO will accept for a direct
 * PUT — useful for integration testing with the docker-compose stack.
 */
@Component
@Profile("local")
public class LocalSignedUrlAdapter implements SignedUrlPort {

    private static final Logger log = LoggerFactory.getLogger(LocalSignedUrlAdapter.class);

    private final String minioEndpoint;
    private final String quarantineBucket;

    /**
     * @param minioEndpoint     MinIO S3 API endpoint, e.g. {@code http://localhost:9000}
     * @param quarantineBucket  quarantine bucket name
     */
    public LocalSignedUrlAdapter(
            @Value("${cams.storage.endpoint:http://localhost:9000}") String minioEndpoint,
            @Value("${cams.storage.bucket-quarantine:cams-quarantine}") String quarantineBucket) {
        this.minioEndpoint    = minioEndpoint;
        this.quarantineBucket = quarantineBucket;
        log.info("LocalSignedUrlAdapter initialised — endpoint={}, bucket={}",
                minioEndpoint, quarantineBucket);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns a plain MinIO upload URL. In a local Docker environment this is
     * sufficient for development testing. For production-equivalent fidelity,
     * this should be replaced with an AWS SDK v2 pre-signed URL against MinIO.
     */
    @Override
    public URL generateSignedPutUrl(String objectName) {
        // Construct a simple direct-upload URL to MinIO with a fake expiry token.
        // TODO T207-E: use AWS SDK v2 S3Presigner with MinIO endpoint for proper signed URLs.
        String rawUrl = minioEndpoint + "/" + quarantineBucket + "/" + objectName
                + "?X-Amz-Expires=900&X-Amz-Date=" + Instant.now().toEpochMilli();
        log.debug("LocalSignedUrlAdapter — generated upload URL for object={}", objectName);
        try {
            return new URL(rawUrl);
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Failed to construct local MinIO upload URL: " + rawUrl, e);
        }
    }
}
