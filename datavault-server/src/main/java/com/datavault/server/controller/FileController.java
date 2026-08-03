package com.datavault.server.controller;

import com.datavault.server.dto.FileMetadataDto;
import com.datavault.server.dto.FileUploadResponse;
import com.datavault.server.service.FileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.UUID;

/**
 * FileController handles high-performance REST file transfers with multi-tenant user isolation.
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * Accepts either raw HTTP binary stream or multipart file upload for current authenticated user.
     */
    @PostMapping(value = "/upload", consumes = {MediaType.ALL_VALUE})
    public ResponseEntity<FileUploadResponse> uploadFile(
            @RequestParam(value = "file", required = false) MultipartFile multipartFile,
            @RequestHeader(value = "X-File-Name", required = false) String headerFileName,
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            HttpServletRequest request,
            Principal principal) throws IOException {

        InputStream inputStream;
        String fileName;

        if (multipartFile != null && !multipartFile.isEmpty()) {
            inputStream = multipartFile.getInputStream();
            fileName = multipartFile.getOriginalFilename();
            if (contentType == null || contentType.isBlank() || contentType.equals(MediaType.MULTIPART_FORM_DATA_VALUE)) {
                contentType = multipartFile.getContentType();
            }
        } else {
            inputStream = request.getInputStream();
            fileName = (headerFileName != null && !headerFileName.isBlank()) 
                    ? headerFileName 
                    : "upload_" + System.currentTimeMillis();
        }

        String username = (principal != null) ? principal.getName() : "admin";
        FileUploadResponse response = fileService.uploadFileStream(inputStream, fileName, contentType, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Download complete file with attachment disposition and SHA-256 ETag.
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable UUID id) {
        FileMetadataDto metadata = fileService.getFileMetadata(id);
        Resource resource = fileService.loadFileResource(id);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(metadata.fileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(metadata.contentType()))
                .contentLength(metadata.fileSize())
                .eTag("\"" + metadata.sha256Checksum() + "\"")
                .body(resource);
    }

    /**
     * Stream file content supporting HTTP 206 Partial Content Range header.
     */
    @GetMapping("/stream/{id}")
    public ResponseEntity<InputStreamResource> streamFile(
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {

        FileMetadataDto metadata = fileService.getFileMetadata(id);
        long fileSize = metadata.fileSize();

        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            Resource fullResource = fileService.loadFileResource(id);
            try {
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(metadata.contentType()))
                        .contentLength(fileSize)
                        .body(new InputStreamResource(fullResource.getInputStream()));
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }

        String rangeValues = rangeHeader.substring(6).trim();
        long startByte = 0;
        long endByte = fileSize - 1;

        if (rangeValues.contains("-")) {
            String[] parts = rangeValues.split("-", -1);
            if (!parts[0].isBlank()) {
                startByte = Long.parseLong(parts[0]);
            }
            if (parts.length > 1 && !parts[1].isBlank()) {
                endByte = Long.parseLong(parts[1]);
            }
        }

        if (endByte >= fileSize) {
            endByte = fileSize - 1;
        }

        long contentLength = endByte - startByte + 1;
        InputStream chunkStream = fileService.loadChunkStream(id, startByte, endByte);

        String contentRange = String.format("bytes %d-%d/%d", startByte, endByte, fileSize);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.CONTENT_RANGE, contentRange)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(MediaType.parseMediaType(metadata.contentType()))
                .contentLength(contentLength)
                .body(new InputStreamResource(chunkStream));
    }

    /**
     * Paginated metadata listing for authenticated user.
     */
    @GetMapping
    public ResponseEntity<Page<FileMetadataDto>> listFiles(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Principal principal) {
        String username = (principal != null) ? principal.getName() : "admin";
        Page<FileMetadataDto> files = fileService.listFiles(username, pageable);
        return ResponseEntity.ok(files);
    }

    /**
     * Delete physical storage file and database record.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable UUID id) {
        fileService.deleteFile(id);
        return ResponseEntity.noContent().build();
    }
}
