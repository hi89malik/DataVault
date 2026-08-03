package com.datavault.server.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record FileUploadResponse(
    UUID id,
    String fileName,
    Long fileSize,
    String contentType,
    String sha256Checksum,
    LocalDateTime createdAt,
    String message
) {}
