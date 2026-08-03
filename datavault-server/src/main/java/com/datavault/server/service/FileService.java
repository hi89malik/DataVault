package com.datavault.server.service;

import com.datavault.server.dto.FileMetadataDto;
import com.datavault.server.dto.FileUploadResponse;
import com.datavault.server.entity.FileMetadata;
import com.datavault.server.exception.FileNotFoundException;
import com.datavault.server.repository.FileMetadataRepository;
import com.datavault.server.storage.StorageService;
import com.datavault.server.storage.StoredFileResult;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class FileService {

    private final StorageService storageService;
    private final FileMetadataRepository metadataRepository;

    public FileService(StorageService storageService, FileMetadataRepository metadataRepository) {
        this.storageService = storageService;
        this.metadataRepository = metadataRepository;
    }

    @Transactional
    public FileUploadResponse uploadFileStream(InputStream inputStream, String fileName, String contentType, String ownerUsername) {
        if (fileName == null || fileName.isBlank()) {
            fileName = "unnamed_file_" + System.currentTimeMillis();
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        if (ownerUsername == null || ownerUsername.isBlank()) {
            ownerUsername = "admin";
        }

        StoredFileResult result = storageService.saveFile(inputStream, fileName);

        FileMetadata metadata = new FileMetadata();
        metadata.setFileName(fileName);
        metadata.setFileSize(result.fileSize());
        metadata.setContentType(contentType);
        metadata.setSha256Checksum(result.sha256Checksum());
        metadata.setStoragePath(result.storagePath());
        metadata.setOwnerUsername(ownerUsername);
        metadata.setCreatedAt(LocalDateTime.now());

        FileMetadata savedMetadata = metadataRepository.save(metadata);

        return new FileUploadResponse(
            savedMetadata.getId(),
            savedMetadata.getFileName(),
            savedMetadata.getFileSize(),
            savedMetadata.getContentType(),
            savedMetadata.getSha256Checksum(),
            savedMetadata.getCreatedAt(),
            "File uploaded and indexed successfully"
        );
    }

    @Transactional(readOnly = true)
    public FileMetadataDto getFileMetadata(UUID id) {
        FileMetadata metadata = metadataRepository.findById(id)
            .orElseThrow(() -> new FileNotFoundException("File metadata record not found for ID: " + id));
        return FileMetadataDto.fromEntity(metadata);
    }

    @Transactional(readOnly = true)
    public Page<FileMetadataDto> listFiles(String ownerUsername, Pageable pageable) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            return metadataRepository.findAll(pageable).map(FileMetadataDto::fromEntity);
        }
        return metadataRepository.findByOwnerUsername(ownerUsername, pageable)
            .map(FileMetadataDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public Resource loadFileResource(UUID id) {
        FileMetadata metadata = metadataRepository.findById(id)
            .orElseThrow(() -> new FileNotFoundException("File not found with ID: " + id));
        return storageService.loadFileAsResource(metadata.getStoragePath());
    }

    @Transactional(readOnly = true)
    public InputStream loadChunkStream(UUID id, long startByte, long endByte) {
        FileMetadata metadata = metadataRepository.findById(id)
            .orElseThrow(() -> new FileNotFoundException("File not found with ID: " + id));
        
        long fileSize = metadata.getFileSize();
        if (startByte < 0 || startByte >= fileSize || endByte < startByte || endByte >= fileSize) {
            throw new IllegalArgumentException(String.format("Invalid range [%d-%d] for file of size %d", startByte, endByte, fileSize));
        }

        return storageService.loadChunk(metadata.getStoragePath(), startByte, endByte);
    }

    @Transactional
    public void deleteFile(UUID id) {
        FileMetadata metadata = metadataRepository.findById(id)
            .orElseThrow(() -> new FileNotFoundException("File not found with ID: " + id));

        storageService.deleteFile(metadata.getStoragePath());
        metadataRepository.delete(metadata);
    }
}
