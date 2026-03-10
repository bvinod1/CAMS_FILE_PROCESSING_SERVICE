package com.cams.fileprocessing.gcp;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class GcsService {

    private final Storage storage;
    private final String quarantineBucket;

    public GcsService(Storage storage, @Value("${gcp.storage.quarantine-bucket}") String quarantineBucket) {
        this.storage = storage;
        this.quarantineBucket = quarantineBucket;
    }

    public URL generateV4PutObjectSignedUrl(String objectName) {
        BlobInfo blobInfo = BlobInfo.newBuilder(quarantineBucket, objectName).build();

        // Set the content type if you know it, or allow the client to set it
        Map<String, String> extensionHeaders = new HashMap<>();
        extensionHeaders.put("Content-Type", "application/octet-stream");

        return storage.signUrl(
                blobInfo,
                15, // URL expiration time
                TimeUnit.MINUTES,
                Storage.SignUrlOption.httpMethod(Storage.HttpMethod.PUT),
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.withExtHeaders(extensionHeaders)
        );
    }
}
