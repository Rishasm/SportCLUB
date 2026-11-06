package ru.sportclub.club.controller;

import ru.sportclub.club.service.ClubService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static ru.sportclub.club.dto.ClubModels.*;

@RestController
@RequestMapping("/api/club")
@Tag(name = "Клуб и бронирования")
public class ClubController {
    private final ClubService club;

    public ClubController(ClubService club) { this.club = club; }

    @GetMapping("/trainers")
    @Operation(summary = "Тренеры клуба")
    public List<Trainer> trainers() { return club.trainers(); }

    @GetMapping("/calendar")
    public CalendarRange calendar() { return club.calendar(); }

    @GetMapping("/classes/{id}")
    public ClassDetails details(@PathVariable UUID id, @AuthenticationPrincipal Jwt principal) {
        return club.details(id, principal == null ? null : UUID.fromString(principal.getSubject()));
    }

    @GetMapping("/me/notices")
    @SecurityRequirement(name = "bearerAuth")
    public List<Notice> notices(@AuthenticationPrincipal Jwt principal) {
        return club.notices(UUID.fromString(principal.getSubject()));
    }

    @GetMapping("/classes")
    @Operation(summary = "Будущие занятия на выбранный день")
    public List<ClassView> classes(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                   @RequestParam(defaultValue = "ALL") String type,
                                   @AuthenticationPrincipal Jwt principal) {
        return club.classes(date == null ? LocalDate.now(ClubService.MOSCOW) : date, type,
                principal == null ? null : UUID.fromString(principal.getSubject()));
    }

    @GetMapping("/me/bookings")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Записи текущего клиента")
    public List<Booking> myBookings(@AuthenticationPrincipal Jwt principal) {
        return club.myBookings(UUID.fromString(principal.getSubject()));
    }

    @PostMapping("/bookings")
    @SecurityRequirement(name = "bearerAuth")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Записаться на занятие с проверкой абонемента")
    public Booking book(@AuthenticationPrincipal Jwt principal, @Valid @RequestBody BookingRequest request) {
        return club.book(UUID.fromString(principal.getSubject()), principal.getClaimAsString("name"),
                principal.getTokenValue(), request.classId());
    }

    @DeleteMapping("/bookings/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Отменить собственную запись")
    public void cancel(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id) {
        club.cancel(UUID.fromString(principal.getSubject()), id);
    }
}
