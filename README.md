# DataVault

DataVault is a Remote File Storage Server and CLI Client built in Java 21 and Spring Boot 3.3.x. The system provides zero-copy streaming file transfers, client-side SHA-256 cryptographic verification, HTTP 206 partial content range streaming, and stateless JWT authentication.

Live Web Dashboard: https://datavault-2xpl.onrender.com/

## Architecture

DataVault consists of two modules:

1. **datavault-server**: A Spring Boot application configured with Java 21 Virtual Threads. It streams incoming file transfers through a pluggable storage layer supporting both local NIO disk channels and S3-compatible cloud object storage (Cloudflare R2 / Google Cloud Storage).
2. **datavault-cli**: A Java 21 command-line client built using java.net.http.HttpClient with streaming request publishers, terminal progress tracking, and client-side SHA-256 integrity checks.

```
+------------------+         HTTP / JWT         +------------------+
|  datavault-cli   | <------------------------> | datavault-server |
+------------------+                            +--------+---------+
                                                         |
                                        +----------------+----------------+
                                        |                                 |
                                        v                                 v
                             +--------------------+            +--------------------+
                             | StorageService     |            | JPA Metadata       |
                             | (Local NIO / S3 R2)|            | (PostgreSQL / H2)  |
                             +--------------------+            +--------------------+
```

## Technical Features

- **Java 21 Virtual Threads**: Enables high concurrent streaming I/O throughput without blocking platform threads.
- **Bounded Memory Streaming**: Processes file uploads and downloads using fixed 64KB buffers (FileChannel and ByteBuffer). Memory usage remains constant regardless of file size.
- **On-The-Fly Checksum Calculation**: Calculates SHA-256 hashes during single-pass input stream iteration without secondary disk re-reads.
- **HTTP Range Streaming (206 Partial Content)**: Supports resumable transfers and media seeking via byte-range positioning.
- **S3 / Cloudflare R2 Integration**: Streams payload bytes directly to S3-compatible cloud storage, consuming 0 MB of local disk space on the application server.
- **Client-Side Integrity Verification**: The CLI client computes local SHA-256 checksums and verifies them against the server-provided hash upon transfer completion.

## REST API Endpoints

### Authentication
- `POST /api/v1/auth/register` - Create a new user account
- `POST /api/v1/auth/login` - Authenticate credentials and receive a JWT Bearer token

### File Storage
- `POST /api/v1/files/upload` - Upload file payload (raw binary or multipart stream)
- `GET /api/v1/files` - List metadata records for authenticated user (paginated)
- `GET /api/v1/files/download/{id}` - Download complete file payload with ETag checksum
- `GET /api/v1/files/stream/{id}` - Stream byte range supporting HTTP 206 Range headers
- `DELETE /api/v1/files/{id}` - Delete physical storage payload and database metadata

## Getting Started

### Prerequisites
- JDK 21
- Apache Maven 3.9+

### Build

```bash
mvn clean package
```

### Run Server

```bash
java -jar datavault-server/target/datavault-server-1.0.0.jar
```

The server starts at `http://localhost:8989/`. The web dashboard is accessible at the root path `/`.

### Run CLI Client

```bash
# Register account
java -jar datavault-cli/target/datavault-cli-1.0.0.jar login <username> <password>

# Upload file
java -jar datavault-cli/target/datavault-cli-1.0.0.jar upload <path-to-file>

# List user files
java -jar datavault-cli/target/datavault-cli-1.0.0.jar list

# Download file with SHA-256 verification
java -jar datavault-cli/target/datavault-cli-1.0.0.jar download <file-id> <destination-path>

# Delete file
java -jar datavault-cli/target/datavault-cli-1.0.0.jar delete <file-id>
```

## Configuration

Server settings can be configured via environment variables or `application.yml`:

```yaml
datavault:
  storage:
    type: s3 # 'local' or 's3'
    s3:
      endpoint: https://<account-id>.r2.cloudflarestorage.com
      region: auto
      bucket: datavault-storage
      access-key: <ACCESS_KEY>
      secret-key: <SECRET_KEY>
```
