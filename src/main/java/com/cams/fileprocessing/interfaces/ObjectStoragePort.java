package com.cams.fileprocessing.interfaces;

import reactor.core.publisher.Mono;

import java.io.InputStream;

/**
 * Port interface for reading and writing files in object storage.
 *
 * <p>Local adapter: MinIO via AWS SDK v2.
 * <p>GCP adapter: Google Cloud Storage.
 *
 * <p>Implementations are activated by {@code @Profile("local")} or {@code @Profile("gcp")}.
 * Business and feature classes must only depend on this interface — never on a cloud SDK class.
 */
public interface ObjectStoragePort {

    /**
     * Returns a reactive stream wrapping a live {@link InputStream} for the first {@code maxBytes}
     * bytes of an object. Closes the stream when the Mono is cancelled or completes.
     *
     * @param bucket     storage bucket / MinIO bucket name
     * @param objectPath object key / GCS object path
     * @param maxBytes   maximum number of bytes to read (use {@code Long.MAX_VALUE} for full file)
     * @return a {@link Mono} that emits a single {@link InputStream}; caller must close it
     */
    Mono<InputStream> readFirstBytes(String bucket, String objectPath, long maxBytes);

    /**
     * Writes bytes from {@code content} to the specified object. Used by upload/copy operations.
     *
     * @param bucket      destination bucket
     * @param objectPath  destination object key
     * @param content     data to write
     * @param contentType MIME type (e.g. {@code "text/csv"})
     * @return a {@link Mono} that completes when the write is acknowledged
     */
    Mono<Void> write(String bucket, String objectPath, InputStream content, String contentType);

    /**
     * Copies an object from one bucket to another (used by quarantine → processing promotion).
     *
     * @param sourceBucket      source bucket
     * @param sourceObjectPath  source key
     * @param destBucket        destination bucket
     * @param destObjectPath    destination key
     * @return a {@link Mono} that completes when the copy is acknowledged
     */
    Mono<Void> copy(String sourceBucket, String sourceObjectPath,
                    String destBucket,   String destObjectPath);

    /**
     * Deletes an object. Used to clean up quarantine bucket entries after promotion or rejection.
     *
     * @param bucket     bucket name
     * @param objectPath object key to delete
     * @return a {@link Mono} that completes when the deletion is acknowledged
     */
    Mono<Void> delete(String bucket, String objectPath);
}
