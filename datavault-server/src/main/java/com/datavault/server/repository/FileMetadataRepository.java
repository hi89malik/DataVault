package com.datavault.server.repository;

import com.datavault.server.entity.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for FileMetadata entity supporting pagination, sorting, and lookup.
 */
@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {
    Optional<FileMetadata> findBySha256Checksum(String sha256Checksum);
    org.springframework.data.domain.Page<FileMetadata> findByOwnerUsername(String ownerUsername, org.springframework.data.domain.Pageable pageable);
}
