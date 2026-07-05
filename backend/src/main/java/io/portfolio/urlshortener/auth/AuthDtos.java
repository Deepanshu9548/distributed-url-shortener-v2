package io.portfolio.urlshortener.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Records for the {@code /api/auth/**} and {@code /api/me/links} surfaces.
 * Keeping the shapes here (rather than one file per record) avoids sprawl —
 * these are dumb data holders, not entities.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @Email @NotBlank @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 128) String password) {
    }

    public record RegisterResponse(long userId, String email) {
    }

    public record LoginRequest(
            @Email @NotBlank @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 128) String password) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn) {
        public static TokenResponse of(String access, String refresh, long expiresInSeconds) {
            return new TokenResponse(access, refresh, "Bearer", expiresInSeconds);
        }
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(@NotBlank String refreshToken) {
    }

    public record LinkUpdateRequest(
            @Size(max = 8192) String longUrl,
            Instant expiresAt,
            Long ttlSeconds) {
    }

    public record LinkMetaResponse(
            String shortCode,
            String longUrl,
            Instant createdAt,
            Instant expiresAt) {
    }

    public record UserLinkView(String shortCode, Instant createdAt) {
    }

    public record UserLinksPage(
            List<UserLinkView> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
