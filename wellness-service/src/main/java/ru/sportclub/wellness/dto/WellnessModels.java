package ru.sportclub.wellness.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class WellnessModels {
    private WellnessModels() {}

    public record WaterEntry(UUID id, int amountMl, OffsetDateTime createdAt) {}
    public record Day(LocalDate date, LocalDate today, int waterGoalMl, int waterTotalMl,
                      List<WaterEntry> waterEntries, Integer sleepMinutes,
                      BigDecimal weightKg, BigDecimal heightCm, BigDecimal bmi) {}

    public record WaterRequest(
            @NotNull(message="Укажите дату записи.") LocalDate date,
            @NotNull(message="Укажите объём воды.")
            @Min(value=1, message="Объём должен быть не меньше 1 мл.")
            @Max(value=2000, message="За один раз можно добавить до 2000 мл.") Integer amountMl) {}
    public record SettingsRequest(
            @NotNull(message="Укажите личную цель по воде.")
            @Min(value=250, message="Личная цель должна быть не меньше 250 мл.")
            @Max(value=10000, message="Личная цель должна быть не больше 10000 мл.") Integer waterGoalMl) {}
    public record SleepRequest(
            @NotNull(message="Укажите длительность сна в минутах.")
            @Min(value=15, message="Длительность сна должна быть не меньше 15 минут.")
            @Max(value=1440, message="Длительность сна должна быть не больше 24 часов.") Integer sleepMinutes) {}
    public record BodyRequest(
            @NotNull(message="Укажите рост.")
            @DecimalMin(value="100", message="Рост должен быть от 100 до 250 см.")
            @DecimalMax(value="250", message="Рост должен быть от 100 до 250 см.")
            @Digits(integer=3, fraction=2, message="Для роста допустимо до двух знаков после запятой.") BigDecimal heightCm,
            @NotNull(message="Укажите вес.")
            @DecimalMin(value="30", message="Вес должен быть от 30 до 300 кг.")
            @DecimalMax(value="300", message="Вес должен быть от 30 до 300 кг.")
            @Digits(integer=3, fraction=2, message="Для веса допустимо до двух знаков после запятой.") BigDecimal weightKg) {}
}
