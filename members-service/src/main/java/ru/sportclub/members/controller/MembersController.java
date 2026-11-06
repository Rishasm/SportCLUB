package ru.sportclub.members.controller;

import ru.sportclub.members.service.MembersService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static ru.sportclub.members.dto.MembersDtos.*;

@RestController
@RequestMapping("/api/members")
@Tag(name = "Клиенты и абонементы")
public class MembersController {
    private final MembersService members;

    public MembersController(MembersService members) {
        this.members = members;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать аккаунт клиента")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return members.register(request);
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Войти по email и паролю")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return members.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Получить свой профиль и абонемент")
    @SecurityRequirement(name = "bearerAuth")
    public Profile me(@AuthenticationPrincipal Jwt jwt) {
        return members.profile(UUID.fromString(jwt.getSubject()));
    }

    @PutMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public AuthResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest request) {
        return members.updateProfile(UUID.fromString(jwt.getSubject()), request);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    public void password(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordRequest request) {
        members.changePassword(UUID.fromString(jwt.getSubject()), request);
    }

    @PostMapping("/me/payments")
    @SecurityRequirement(name = "bearerAuth")
    public PaymentResult pay(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PaymentRequest request) {
        return members.checkout(UUID.fromString(jwt.getSubject()), request);
    }

    @GetMapping("/me/payment-card")
    @SecurityRequirement(name = "bearerAuth")
    public SavedCard card(@AuthenticationPrincipal Jwt jwt) {
        return members.savedCard(UUID.fromString(jwt.getSubject()));
    }

    @PutMapping("/me/payment-card")
    @SecurityRequirement(name = "bearerAuth")
    public SavedCard linkCard(@AuthenticationPrincipal Jwt jwt) {
        return members.setCard(UUID.fromString(jwt.getSubject()), true);
    }

    @DeleteMapping("/me/payment-card")
    @SecurityRequirement(name = "bearerAuth")
    public SavedCard unlinkCard(@AuthenticationPrincipal Jwt jwt) {
        return members.setCard(UUID.fromString(jwt.getSubject()), false);
    }

    @GetMapping("/plans")
    @Operation(summary = "Посмотреть доступные тарифы")
    public List<Plan> plans() {
        return members.plans();
    }

    @PostMapping("/me/membership")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Оформить учебный абонемент без реального платежа")
    @SecurityRequirement(name = "bearerAuth")
    public Membership activate(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody ActivateMembershipRequest request) {
        return members.activate(UUID.fromString(jwt.getSubject()), request.planId());
    }

    @GetMapping("/me/access")
    @Operation(summary = "Проверить, действует ли собственный абонемент")
    @SecurityRequirement(name = "bearerAuth")
    public Access access(@AuthenticationPrincipal Jwt jwt) {
        return members.access(UUID.fromString(jwt.getSubject()));
    }
}

