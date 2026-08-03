package com.datavault.server.dto;

public record AuthResponse(
    String token,
    String tokenType,
    long expiresInSeconds
) {
    public AuthResponse(String token, long expiresInSeconds) {
        this(token, "Bearer", expiresInSeconds);
    }
}
