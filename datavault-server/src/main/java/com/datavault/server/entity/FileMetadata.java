package com.datavault.server.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * FileMetadata domain entity representing uploaded file records in PostgreSQL/H2 database.
 * Stores lightweight descriptive properties and SHA-256 integrity checksums.
 */
@Entity
@Table(name = "file_metadata", indexes = {
    @Index(name = "idx_file_created_at", columnList = "createdAt"),
    @Index(name = "idx_file_checksum", columnList = "sha256Checksum")
})
public class FileMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false, length = 64)
    private String sha256Checksum;

    @Column(nullable = false, length = 1024)
    private String storagePath;

    @Column(nullable = false, length = 50)
    private String ownerUsername = "admin";

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public FileMetadata() {
    }

    public FileMetadata(UUID id, String fileName, Long fileSize, String contentType, String sha256Checksum, String storagePath, LocalDateTime createdAt) {
        this.id = id;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.sha256Checksum = sha256Checksum;
        this.storagePath = storagePath;
        this.createdAt = createdAt;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getSha256Checksum() {
        return sha256Checksum;
    }

    public void setSha256Checksum(String sha256Checksum) {
        this.sha256Checksum = sha256Checksum;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
