package com.datavault.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Storage configuration properties for switching storage type (local vs s3)
 * and configuring file locations or S3 credentials.
 */
@Configuration
@ConfigurationProperties(prefix = "datavault.storage")
public class StorageProperties {

    /**
     * Storage implementation type: "local" or "s3"
     */
    private String type = "local";

    /**
     * Local storage directory path
     */
    private String localLocation = "uploads";

    /**
     * S3 / GCS configuration properties
     */
    private S3 s3 = new S3();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLocalLocation() {
        return localLocation;
    }

    public void setLocalLocation(String localLocation) {
        this.localLocation = localLocation;
    }

    public S3 getS3() {
        return s3;
    }

    public void setS3(S3 s3) {
        this.s3 = s3;
    }

    public static class S3 {
        private String endpoint = "https://s3.amazonaws.com";
        private String region = "us-east-1";
        private String bucket = "datavault-bucket";
        private String accessKey = "";
        private String secretKey = "";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
    }
}
