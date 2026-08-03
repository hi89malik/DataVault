package com.datavault.server.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;

/**
 * StorageService abstraction contract defining zero-copy / bounded-memory streaming
 * file storage operations across Local NIO disk or S3/GCS cloud object storage.
 */
public interface StorageService {

    /**
     * Streams input bytes directly into storage using a fixed 64KB buffer,
     * calculating SHA-256 on-the-fly without accumulating heap memory.
     *
     * @param inputStream Source data stream from HTTP request.
     * @param fileName Original or target file name.
     * @return StoredFileResult containing storage reference, byte count, and SHA-256 checksum.
     */
    StoredFileResult saveFile(InputStream inputStream, String fileName);

    /**
     * Retrieves the entire file resource for full download.
     *
     * @param storagePath Storage reference path or key.
     * @return Spring Resource for streaming download.
     */
    Resource loadFileAsResource(String storagePath);

    /**
     * Opens a chunk input stream starting at startByte up to endByte for HTTP 206 Range requests.
     *
     * @param storagePath Storage reference path or key.
     * @param startByte Inclusive start byte offset.
     * @param endByte Inclusive end byte offset.
     * @return InputStream containing exact range bytes.
     */
    InputStream loadChunk(String storagePath, long startByte, long endByte);

    /**
     * Purges file payload from storage.
     *
     * @param storagePath Storage reference path or key.
     * @return true if successfully removed.
     */
    boolean deleteFile(String storagePath);
}
