package com.cams.fileprocessing.features.scan;

import java.time.Instant;

/**
 * Domain event published when a file has been confirmed as uploaded to the quarantine bucket.
 *
 * <p>This event is the trigger for the malware scanning pipeline. The Scan Worker
 * consumes this event from the message queue (RabbitMQ on local profile,
 * GCP Pub/Sub on gcp profile) and initiates the ClamAV scan.
 *
 * @param eventId          unique identifier for this event occurrence (UUID)
 * @param fileId           the uploaded file's unique identifier
 * @param originalFileName the original filename as supplied by the client
 * @param flowType         business classification (e.g. {@code "NAV"}, {@code "TRANSACTION"})
 * @param ingressChannel   how the file entered the system ({@code "REST"}, {@code "SFTP"}, {@code "GCS_TRIGGER"})
 * @param checksumMd5      client-supplied MD5 checksum of the file
 * @param storageBucket    the quarantine bucket name where the file resides
 * @param storageObjectPath the full object path within the bucket
 * @param priority         processing priority (0 = highest)
 * @param occurredAt       timestamp when the upload was confirmed
 */
public record FileReceivedEvent(
        String  eventId,
        String  fileId,
        String  originalFileName,
        String  flowType,
        String  ingressChannel,
        String  checksumMd5,
        String  storageBucket,
        String  storageObjectPath,
        int     priority,
        Instant occurredAt
) {}
