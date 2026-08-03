package com.datavault.server.storage;

/**
 * Result record returned after streaming storage save operation.
 *
 * @param storagePath Physical storage path or cloud object key.
 * @param fileSize Total bytes written to storage.
 * @param sha256Checksum Computed Hex SHA-256 cryptographic checksum.
 */
public record StoredFileResult(
    String storagePath,
    long fileSize,
    String sha256Checksum
) {}
