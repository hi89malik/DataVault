package com.datavault.cli.client;

import com.datavault.cli.util.ChecksumUtil;
import com.datavault.cli.util.ProgressBar;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Map;

/**
 * DataVaultClient handles HTTP communication with DataVault server using Java 21 HttpClient.
 * Features:
 * - Direct stream upload with progress bar
 * - Direct stream download with local SHA-256 checksum verification
 * - JWT authentication token management
 */
public class DataVaultClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private String bearerToken;

    public DataVaultClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public boolean login(String username, String password) throws IOException, InterruptedException {
        String body = objectMapper.writeValueAsString(Map.of("username", username, "password", password));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode node = objectMapper.readTree(response.body());
            this.bearerToken = node.get("token").asText();
            return true;
        } else {
            System.err.println("Login failed with status " + response.statusCode() + ": " + response.body());
            return false;
        }
    }

    public void uploadFile(Path filePath) throws IOException, InterruptedException {
        if (!Files.exists(filePath)) {
            System.err.println("Error: File does not exist: " + filePath);
            return;
        }

        long fileSize = Files.size(filePath);
        String fileName = filePath.getFileName().toString();
        ProgressBar progressBar = new ProgressBar("Uploading " + fileName, fileSize);

        InputStream fileStream = Files.newInputStream(filePath);
        InputStream progressStream = new FilterInputStream(fileStream) {
            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int bytesRead = super.read(b, off, len);
                if (bytesRead > 0) {
                    progressBar.update(bytesRead);
                }
                return bytesRead;
            }
        };

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/files/upload"))
                .header("X-File-Name", fileName)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> progressStream));

        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        progressBar.finish();

        if (response.statusCode() == 201 || response.statusCode() == 200) {
            JsonNode node = objectMapper.readTree(response.body());
            System.out.println("Upload successful!");
            System.out.println("File ID:        " + node.get("id").asText());
            System.out.println("File Name:      " + node.get("fileName").asText());
            System.out.println("Size:           " + node.get("fileSize").asLong() + " bytes");
            System.out.println("SHA-256 (Server): " + node.get("sha256Checksum").asText());
        } else {
            System.err.println("Upload failed [HTTP " + response.statusCode() + "]: " + response.body());
        }
    }

    public void downloadFile(String fileId, Path destinationPath) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/files/download/" + fileId))
                .GET();

        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }

        HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            System.err.println("Download failed [HTTP " + response.statusCode() + "]");
            return;
        }

        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        String serverETag = response.headers().firstValue("ETag")
                .map(etag -> etag.replace("\"", "")).orElse("");

        ProgressBar progressBar = new ProgressBar("Downloading " + fileId, contentLength);

        try (InputStream is = response.body();
             OutputStream os = Files.newOutputStream(destinationPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            byte[] buffer = new byte[64 * 1024];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                progressBar.update(bytesRead);
            }
        }
        progressBar.finish();

        System.out.println("File saved to: " + destinationPath.toAbsolutePath());

        // Verify SHA-256
        String localChecksum = ChecksumUtil.computeSha256(destinationPath);
        System.out.println("Local SHA-256:  " + localChecksum);
        if (!serverETag.isEmpty()) {
            System.out.println("Server SHA-256: " + serverETag);
            if (localChecksum.equalsIgnoreCase(serverETag)) {
                System.out.println("Integrity Check Passed: SHA-256 hashes match perfectly!");
            } else {
                System.err.println("INTEGRITY ERROR: SHA-256 checksum mismatch!");
            }
        }
    }

    public void listFiles() throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/files?size=50"))
                .GET();

        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.get("content");

            System.out.println("\n================================ DataVault Files ================================");
            System.out.printf("%-38s | %-20s | %-12s | %-20s%n", "File ID", "Name", "Size (KB)", "Created At");
            System.out.println("----------------------------------------------------------------------------------");

            if (content.isArray() && content.size() > 0) {
                for (JsonNode item : content) {
                    String id = item.get("id").asText();
                    String name = item.get("fileName").asText();
                    if (name.length() > 20) name = name.substring(0, 17) + "...";
                    long sizeKb = item.get("fileSize").asLong() / 1024;
                    String createdAt = item.get("createdAt").asText();
                    if (createdAt.length() > 19) createdAt = createdAt.substring(0, 19);

                    System.out.printf("%-38s | %-20s | %-12d | %-20s%n", id, name, sizeKb, createdAt);
                }
            } else {
                System.out.println("No files stored in DataVault.");
            }
            System.out.println("==================================================================================\n");
        } else {
            System.err.println("Failed to list files [HTTP " + response.statusCode() + "]: " + response.body());
        }
    }

    public void deleteFile(String fileId) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/files/" + fileId))
                .DELETE();

        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 204) {
            System.out.println("File " + fileId + " successfully deleted.");
        } else {
            System.err.println("Failed to delete file [HTTP " + response.statusCode() + "]: " + response.body());
        }
    }

    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }
}
