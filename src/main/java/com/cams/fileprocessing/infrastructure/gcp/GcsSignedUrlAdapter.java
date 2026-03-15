package com.cams.fileprocessing.infrastructure.gcp;

import com.cams.fileprocessing.interfaces.SignedUrlPort;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * GCP adapter for {@link SignedUrlPort}.
 *
 * <p>Generates GCS V4 pre-signed PUT URLs. The URL is scoped to the quarantine
 * bucket, expires in 15 minutes, and is restricted to {@code Content-Type: application/octet-stream}.
 *
 * <p>Active when Spring profile {@code gcp} is set.
 */
@Component
@Profile("gcp")
public class GcsSignedUrlAdapter implements SignedUrlPort {

    private static final Logger log = LoggerFactory.getLogger(GcsSignedUrlAdapter.class);

    private static final int EXPIRY_MINUTES = 15;

    private final Storage storage;
    private final String quarantineBucket;

    /**
     * @param storage          GCS {@link Storage} client bean (configured in {@code GcpConfig})
     * @param quarantineBucket bucket name read from {@code gcp.storage.quarantine-bucket}
     */
    public GcsSignedUrlAdapter(
            Storage storage,
            @Value("${gcp.storage.quarantine-bucket}") String quarantineBucket) {
        this.storage         = storage;
        this.quarantineBucket = quarantineBucket;
        log.info("GcsSignedUrlAdapter initialised — bucket={}, expiry={}min",
                quarantineBucket, EXPIRY_MINUTES);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Generates a GCS V4 signed URL for an HTTP PUT operation.
     */
    @Override
    public URL generateSignedPutUrl(String objectName) {
        log.debug("Generating GCS signed PUT URL — bucket={}, object={}", quarantineBucket, objectName);

        BlobInfo blobInfo = BlobInfo.newBuilder(quarantineBucket, objectName).build();

        Map<String, String> extensionHeaders = new HashMap<>();
        extensionHeaders.put("Content-Type", "application/octet-stream");

        URL url = storage.signUrl(
                blobInfo,
                EXPIRY_MINUTES,
                TimeUnit.MINUTES,
                Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.withExtHeaders(extensionHeaders)
        );

        log.debug("GCS signed URL generated successfully — object={}", objectName);
        return url;
    }
}
