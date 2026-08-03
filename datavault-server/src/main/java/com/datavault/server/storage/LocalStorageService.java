package com.datavault.server.storage;

import com.datavault.server.config.StorageProperties;
import com.datavault.server.exception.FileNotFoundException;
import com.datavault.server.exception.StorageException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * LocalStorageService implements StorageService using high-performance Java NIO (FileChannel & Path).
 * <p>
 * Technical Design Decisions:
 * 1. Bounded Memory Streaming (64KB Buffer): Streams incoming HTTP request InputStream directly into
 *    a Java NIO FileChannel using a fixed 64KB byte array buffer. Prevents OutOfMemoryError regardless
 *    of file size (e.g. 10GB+ files consume constant 64KB heap space).
 * 2. On-the-Fly SHA-256 Digest: Computes cryptographic hashes in a single pass during write iteration
 *    without intermediate disk re-reads.
 * 3. HTTP Range / Chunk Channel Seeking: FileChannel.position(startByte) enables O(1) random access
 *    seeking for partial byte-range requests (HTTP 206 Partial Content).
 */
@Service
@ConditionalOnProperty(name = "datavault.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private static final int BUFFER_SIZE = 64 * 1024; // 64KB buffer for optimal NIO streaming throughput
    private final Path rootLocation;

    @Autowired
    public LocalStorageService(StorageProperties storageProperties) {
        this.rootLocation = Paths.get(storageProperties.getLocalLocation()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootLocation);
        } catch (IOException e) {
            throw new StorageException("Could not initialize storage location: " + rootLocation, e);
        }
    }

    @Override
    public StoredFileResult saveFile(InputStream inputStream, String fileName) {
        String uniqueFileName = UUID.randomUUID() + "_" + fileName;
        Path targetPath = this.rootLocation.resolve(uniqueFileName).normalize();

        if (!targetPath.getParent().equals(this.rootLocation)) {
            throw new StorageException("Cannot store file outside target root directory.");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long totalBytesWritten = 0;

            try (FileChannel fileChannel = FileChannel.open(
                    targetPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, bytesRead);
                    while (byteBuffer.hasRemaining()) {
                        fileChannel.write(byteBuffer);
                    }
                    totalBytesWritten += bytesRead;
                }
            }

            String sha256Checksum = HexFormat.of().formatHex(digest.digest());
            return new StoredFileResult(targetPath.toString(), totalBytesWritten, sha256Checksum);

        } catch (NoSuchAlgorithmException e) {
            throw new StorageException("SHA-256 algorithm not available", e);
        } catch (IOException e) {
            // Clean up partial file on failure
            try {
                Files.deleteIfExists(targetPath);
            } catch (IOException ignored) {}
            throw new StorageException("Failed to store file: " + fileName, e);
        }
    }

    @Override
    public Resource loadFileAsResource(String storagePath) {
        Path path = Paths.get(storagePath);
        if (!Files.exists(path) || !Files.isReadable(path)) {
            throw new FileNotFoundException("File not found or not readable at: " + storagePath);
        }
        return new FileSystemResource(path);
    }

    @Override
    public InputStream loadChunk(String storagePath, long startByte, long endByte) {
        Path path = Paths.get(storagePath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("File not found at: " + storagePath);
        }

        try {
            FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
            channel.position(startByte);

            long maxBytesToRead = endByte - startByte + 1;
            InputStream channelInputStream = Channels.newInputStream(channel);

            return new FilterInputStream(channelInputStream) {
                private long bytesRemaining = maxBytesToRead;

                @Override
                public int read() throws IOException {
                    if (bytesRemaining <= 0) return -1;
                    int b = super.read();
                    if (b != -1) bytesRemaining--;
                    return b;
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    if (bytesRemaining <= 0) return -1;
                    int maxToRead = (int) Math.min(len, bytesRemaining);
                    int bytesRead = super.read(b, off, maxToRead);
                    if (bytesRead != -1) {
                        bytesRemaining -= bytesRead;
                    }
                    return bytesRead;
                }

                @Override
                public void close() throws IOException {
                    super.close();
                    channel.close();
                }
            };

        } catch (IOException e) {
            throw new StorageException("Failed to read chunk from file: " + storagePath, e);
        }
    }

    @Override
    public boolean deleteFile(String storagePath) {
        try {
            Path path = Paths.get(storagePath);
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new StorageException("Could not delete storage file: " + storagePath, e);
        }
    }
}
