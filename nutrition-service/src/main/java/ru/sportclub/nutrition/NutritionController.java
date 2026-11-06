package ru.sportclub.nutrition;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ru.sportclub.nutrition.NutritionModels.*;

@RestController
@RequestMapping("/api/nutrition")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Дневник питания")
public class NutritionController {
    private final NutritionService nutrition;

    public NutritionController(NutritionService nutrition) { this.nutrition = nutrition; }

    @GetMapping("/foods")
    public List<Food> foods() { return nutrition.foods(); }

    @GetMapping("/day")
    public Day day(@AuthenticationPrincipal Jwt jwt, @RequestParam LocalDate date) {
        return nutrition.day(member(jwt), date);
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EntryRequest request) {
        return Map.of("id", nutrition.add(member(jwt), request));
    }

    @PutMapping("/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody EntryRequest request) {
        nutrition.update(member(jwt), id, request);
    }

    @DeleteMapping("/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        nutrition.delete(member(jwt), id);
    }

    private static UUID member(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
