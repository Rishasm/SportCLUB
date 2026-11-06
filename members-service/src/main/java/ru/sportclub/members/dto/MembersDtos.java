package ru.sportclub.members.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class MembersDtos {
    private MembersDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 80) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 256) String password) {}

    public record ActivateMembershipRequest(@NotBlank @Size(max = 40) String planId) {}

    public record ProfileRequest(@NotBlank @Size(min = 2, max = 80) String name,
                                 @NotBlank @Email @Size(max = 254) String email) {}

    public record PasswordRequest(@NotBlank @Size(max = 72) String currentPassword,
                                  @NotBlank @Size(min = 8, max = 72) String newPassword) {}

    public enum PaymentMethod { CARD, SBP }

    public record PaymentRequest(@NotNull UUID id, @NotBlank @Size(max = 40) String planId,
                                 @NotNull PaymentMethod method, boolean linkCard) {}

    public record PaymentResult(UUID id, PaymentMethod method, int amountRub, String status, Membership membership) {}

    public record SavedCard(boolean linked, String lastFour) {}

    public record User(UUID id, String name, String email, String role) {}

    public record AuthResponse(String token, long expiresIn, User user) {}

    public record Profile(UUID id, String name, String email, String role, Membership membership) {}

    public record Plan(String id, String code, String name, String description, int priceRub,
                       int durationDays, List<String> features) {}

    public record Membership(UUID id, String planId, String planName, OffsetDateTime startsAt,
                             OffsetDateTime validUntil, String status) {}

    public record Access(UUID memberId, boolean active, OffsetDateTime validUntil) {}
}

