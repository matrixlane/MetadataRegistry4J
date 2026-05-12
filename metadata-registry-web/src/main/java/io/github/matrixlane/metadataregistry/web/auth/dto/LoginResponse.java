package io.github.matrixlane.metadataregistry.web.auth.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        UserProfileResponse profile) {

    public static LoginResponse of(String token, long expiresInSeconds, UserProfileResponse profile) {
        return new LoginResponse(token, "Bearer", expiresInSeconds, profile);
    }
}
