package com.datavault.server.storage;

import com.datavault.server.config.StorageProperties;
import com.datavault.server.exception.FileNotFoundException;
import com.datavault.server.exception.StorageException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * S3StorageService implements StorageService for Cloud Object Storage (AWS S3, Google Cloud Storage,
 * Cloudflare R2, or MinIO).
 * <p>
 * Zero Local Disk Usage:
 * Incoming file bytes are passed directly to S3 via S3 API calls without saving any temporary
 * files to local server disk, keeping local hard drive usage at 0 MB.
 */
@Service
@ConditionalOnProperty(name = "datavault.storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {

    private static final int BUFFER_SIZE = 64 * 1024;
    private final S3Client s3Client;
    private final String bucketName;

    @Autowired
    public S3StorageService(StorageProperties storageProperties) {
        StorageProperties.S3 s3Props = storageProperties.getS3();
        this.bucketName = s3Props.getBucket();

        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(s3Props.getEndpoint()))
                .region(Region.of(s3Props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3Props.getAccessKey(), s3Props.getSecretKey())))
                .serviceConfiguration(s3Config)
                .build();
    }

    @Override
    public StoredFileResult saveFile(InputStream inputStream, String fileName) {
        String objectKey = UUID.randomUUID() + "_" + fileName;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
                baos.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }

            byte[] dataBytes = baos.toByteArray();
            String sha256Checksum = HexFormat.of().formatHex(digest.digest());

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentLength(totalBytes)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(new ByteArrayInputStream(dataBytes), totalBytes));

            return new StoredFileResult(objectKey, totalBytes, sha256Checksum);

        } catch (NoSuchAlgorithmException e) {
            throw new StorageException("SHA-256 algorithm not found", e);
        } catch (IOException e) {
            throw new StorageException("Failed to stream upload to S3 cloud storage", e);
        } catch (S3Exception e) {
            throw new StorageException("S3 Storage service error: " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    @Override
    public Resource loadFileAsResource(String storagePath) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storagePath)
                    .build();

            ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getRequest);
            return new InputStreamResource(s3Stream);

        } catch (NoSuchKeyException e) {
            throw new FileNotFoundException("S3 Object key not found: " + storagePath, e);
        } catch (S3Exception e) {
            throw new StorageException("Failed to read S3 object: " + storagePath, e);
        }
    }

    @Override
    public InputStream loadChunk(String storagePath, long startByte, long endByte) {
        try {
            String rangeHeader = "bytes=" + startByte + "-" + endByte;
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storagePath)
                    .range(rangeHeader)
                    .build();

            return s3Client.getObject(getRequest);

        } catch (S3Exception e) {
            throw new StorageException("Failed to load chunk from S3: " + storagePath, e);
        }
    }

    @Override
    public boolean deleteFile(String storagePath) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storagePath)
                    .build();
            s3Client.deleteObject(deleteRequest);
            return true;
        } catch (S3Exception e) {
            throw new StorageException("Failed to delete S3 object: " + storagePath, e);
        }
    }
}
