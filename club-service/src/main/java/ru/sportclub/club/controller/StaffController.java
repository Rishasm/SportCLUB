package ru.sportclub.club.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.sportclub.club.service.ClubService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static ru.sportclub.club.dto.ClubModels.*;

@RestController
@RequestMapping("/api/club")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Расписание для сотрудников")
public class StaffController {
    private final ClubService club;

    public StaffController(ClubService club) { this.club = club; }

    @GetMapping("/admin/classes")
    public List<ClassDetails> adminClasses(@RequestParam LocalDate date) {
        return club.staffClasses(date, null);
    }

    @PostMapping("/admin/classes")
    @ResponseStatus(HttpStatus.CREATED)
    public ClassDetails create(@Valid @RequestBody ClassEdit request) {
        return club.saveClass(null, request);
    }

    @PutMapping("/admin/classes/{id}")
    public ClassDetails update(@PathVariable UUID id, @Valid @RequestBody ClassEdit request) {
        return club.saveClass(id, request);
    }

    @DeleteMapping("/admin/classes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id) { club.removeClass(id); }

    @PostMapping("/admin/announcements")
    @ResponseStatus(HttpStatus.CREATED)
    public Notice announce(@Valid @RequestBody Announcement request) { return club.announce(request); }

    @GetMapping("/trainer/classes")
    public List<ClassDetails> trainerClasses(@AuthenticationPrincipal Jwt jwt, @RequestParam LocalDate date) {
        return club.staffClasses(date, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/trainer/classes/{id}/participants")
    public List<Participant> participants(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return club.participants(UUID.fromString(jwt.getSubject()), id);
    }

    @PutMapping("/trainer/classes/{id}/content")
    public ClassDetails content(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                @Valid @RequestBody ClassContent request) {
        return club.updateContent(UUID.fromString(jwt.getSubject()), id, request);
    }
}
