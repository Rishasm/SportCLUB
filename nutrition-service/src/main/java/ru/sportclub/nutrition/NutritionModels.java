package ru.sportclub.nutrition;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NutritionModels {
    private NutritionModels() {}

    public enum Meal { BREAKFAST, LUNCH, DINNER, SNACK }

    public record EntryRequest(@NotNull LocalDate date, @NotNull Meal meal,
                               @NotBlank @Size(max = 40) String foodId, @Min(1) @Max(3000) int grams) {}

    public record Nutrients(BigDecimal kcal, BigDecimal protein, BigDecimal fat, BigDecimal carbs) {
        public static final Nutrients ZERO = new Nutrients(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        public Nutrients portion(int grams) {
            BigDecimal factor = BigDecimal.valueOf(grams, 2);
            return new Nutrients(kcal.multiply(factor), protein.multiply(factor), fat.multiply(factor), carbs.multiply(factor));
        }

        public Nutrients plus(Nutrients other) {
            return new Nutrients(kcal.add(other.kcal), protein.add(other.protein), fat.add(other.fat), carbs.add(other.carbs));
        }
    }

    public record Food(String id, String name, Nutrients per100g) {}
    public record Entry(UUID id, Meal meal, Food food, int grams, Nutrients portion) {}
    public record Day(LocalDate date, List<Entry> entries, Map<Meal, Nutrients> meals, Nutrients total) {}
}
