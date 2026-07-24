package io.github.matrixlane.metadataregistry.web.auth.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        AccountProfileResponse user) {

    public static LoginResponse of(String token, long expiresInSeconds, AccountProfileResponse user) {
        return new LoginResponse(token, "Bearer", expiresInSeconds, user);
    }
}
