package com.cams.fileprocessing.features.upload;

/**
 * Response DTO returned after a successful upload URL request.
 *
 * @param fileId    the UUID assigned to this file record in Spanner
 * @param uploadUrl the pre-signed GCS URL the client should use for the binary PUT upload
 */
public record UploadResponse(String fileId, String uploadUrl) {
}
