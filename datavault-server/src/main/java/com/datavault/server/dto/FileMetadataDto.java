package com.datavault.server.dto;

import com.datavault.server.entity.FileMetadata;

import java.time.LocalDateTime;
import java.util.UUID;

public record FileMetadataDto(
    UUID id,
    String fileName,
    Long fileSize,
    String contentType,
    String sha256Checksum,
    String storagePath,
    LocalDateTime createdAt
) {
    public static FileMetadataDto fromEntity(FileMetadata metadata) {
        return new FileMetadataDto(
            metadata.getId(),
            metadata.getFileName(),
            metadata.getFileSize(),
            metadata.getContentType(),
            metadata.getSha256Checksum(),
            metadata.getStoragePath(),
            metadata.getCreatedAt()
        );
    }
}
