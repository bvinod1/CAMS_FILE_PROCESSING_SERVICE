package com.cams.fileprocessing.features.upload;

import java.util.UUID;

public record UploadResponse(UUID fileId, String uploadUrl) {
}
