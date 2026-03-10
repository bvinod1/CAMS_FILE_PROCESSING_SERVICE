package com.cams.fileprocessing.features.upload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Immutable request DTO for initiating a secure file upload.
 * All fields are validated via Bean Validation constraints before the request is processed.
 */
public record UploadRequest(
        @NotBlank(message = "File name cannot be blank")
        @Size(max = 1024, message = "File name cannot exceed 1024 characters")
        String fileName,

        @NotBlank(message = "Flow type cannot be blank")
        @Size(max = 255, message = "Flow type cannot exceed 255 characters")
        String flowType,

        @NotBlank(message = "Checksum cannot be blank")
        @Pattern(regexp = "^[a-fA-F0-9]{32}$", message = "Checksum must be a valid MD5 hash")
        String checksum
) {
}
