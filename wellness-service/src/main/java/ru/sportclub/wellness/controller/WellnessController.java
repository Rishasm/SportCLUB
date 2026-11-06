package ru.sportclub.wellness.controller;

import ru.sportclub.wellness.service.WellnessService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static ru.sportclub.wellness.dto.WellnessModels.*;

@RestController
@RequestMapping("/api/wellness")
@Tag(name="Личные показатели")
@SecurityRequirement(name="bearerAuth")
public class WellnessController {
    private final WellnessService service;
    public WellnessController(WellnessService service) { this.service = service; }
    private UUID member(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }

    @GetMapping("/day")
    @Operation(summary="Вода, сон, вес и ИМТ на выбранный день; по умолчанию сегодня по Москве")
    public Day day(@AuthenticationPrincipal Jwt jwt,
                   @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.day(member(jwt), date == null ? service.today() : date);
    }

    @GetMapping("/history")
    @Operation(summary="История показателей за период до 31 дня включительно")
    public List<Day> history(@AuthenticationPrincipal Jwt jwt,
                             @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.history(member(jwt), from, to);
    }

    @PutMapping("/settings")
    @Operation(summary="Сохранить собственную цель по воде; возвращает сегодняшний день")
    public Day settings(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SettingsRequest request) {
        return service.settings(member(jwt), request);
    }

    @PostMapping("/water") @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="Добавить порцию воды за выбранный день")
    public Day water(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody WaterRequest request) {
        return service.addWater(member(jwt), request);
    }

    @DeleteMapping("/water/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary="Удалить собственную запись воды")
    public void deleteWater(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.deleteWater(member(jwt), id);
    }

    @PutMapping("/days/{date}/sleep")
    @Operation(summary="Сохранить общую длительность сна за выбранный день")
    public Day sleep(@AuthenticationPrincipal Jwt jwt,
                     @PathVariable @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,
                     @Valid @RequestBody SleepRequest request) {
        return service.sleep(member(jwt), date, request);
    }

    @DeleteMapping("/days/{date}/sleep") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary="Удалить свою запись сна за выбранный день")
    public void deleteSleep(@AuthenticationPrincipal Jwt jwt,
                            @PathVariable @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date) {
        service.deleteSleep(member(jwt), date);
    }

    @PutMapping("/days/{date}/body")
    @Operation(summary="Сохранить вес и рост; рассчитать ИМТ без медицинской интерпретации")
    public Day body(@AuthenticationPrincipal Jwt jwt,
                    @PathVariable @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,
                    @Valid @RequestBody BodyRequest request) {
        return service.body(member(jwt), date, request);
    }

    @DeleteMapping("/days/{date}/body") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary="Удалить свои вес, рост и ИМТ за выбранный день")
    public void deleteBody(@AuthenticationPrincipal Jwt jwt,
                           @PathVariable @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date) {
        service.deleteBody(member(jwt), date);
    }
}
