package ru.sportclub.wellness.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.sportclub.foundation.ApiException;
import ru.sportclub.wellness.entity.WaterConsumption;
import ru.sportclub.wellness.entity.WellnessDay;
import ru.sportclub.wellness.entity.WellnessDayId;
import ru.sportclub.wellness.entity.WellnessMemberSettings;
import ru.sportclub.wellness.repository.WaterConsumptionRepository;
import ru.sportclub.wellness.repository.WellnessDayRepository;
import ru.sportclub.wellness.repository.WellnessMemberSettingsRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static ru.sportclub.wellness.dto.WellnessModels.*;

@Service
public class WellnessService {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final LocalDate EARLIEST_DATE = LocalDate.of(2000, 1, 1);
    private static final int DEFAULT_WATER_GOAL_ML = 2000;

    private final WellnessMemberSettingsRepository settings;
    private final WellnessDayRepository days;
    private final WaterConsumptionRepository water;
    private final TransactionTemplate transactions;

    public WellnessService(WellnessMemberSettingsRepository settings, WellnessDayRepository days,
                           WaterConsumptionRepository water, TransactionTemplate transactions) {
        this.settings = settings;
        this.days = days;
        this.water = water;
        this.transactions = transactions;
    }

    public LocalDate today() {
        return LocalDate.now(MOSCOW);
    }

    @Transactional(readOnly = true)
    public Day day(UUID memberId, LocalDate date) {
        validateDate(date);
        return loadDay(memberId, date);
    }

    @Transactional(readOnly = true)
    public List<Day> history(UUID memberId, LocalDate from, LocalDate to) {
        validateDate(from);
        validateDate(to);
        long span = ChronoUnit.DAYS.between(from, to);
        if (span < 0 || span > 30) {
            throw badRequest("Выберите период от 1 до 31 дня; начальная дата должна быть не позже конечной.");
        }
        return loadDays(memberId, from, to);
    }

    public Day settings(UUID memberId, SettingsRequest request) {
        return locked(memberId, memberSettings -> {
            memberSettings.setWaterGoalMl(request.waterGoalMl());
            return loadDay(memberId, today());
        });
    }

    public Day addWater(UUID memberId, WaterRequest request) {
        validateDate(request.date());
        return locked(memberId, memberSettings -> {
            long total = water.totalForDay(memberId, request.date());
            if (total + request.amountMl() > 10000) {
                throw new ApiException(HttpStatus.CONFLICT, "WATER_DAY_LIMIT",
                        "За один день можно сохранить не больше 10000 мл воды.");
            }
            water.save(new WaterConsumption(memberId, request.date(), request.amountMl(), OffsetDateTime.now(ZoneOffset.UTC)));
            return loadDay(memberId, request.date());
        });
    }

    public void deleteWater(UUID memberId, UUID id) {
        locked(memberId, memberSettings -> {
            water.delete(water.findByIdAndMemberId(id, memberId).orElseThrow(() ->
                    new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Запись не найдена.")));
            return null;
        });
    }

    public Day sleep(UUID memberId, LocalDate date, SleepRequest request) {
        validateDate(date);
        return locked(memberId, memberSettings -> {
            getOrCreateDay(memberId, date).setSleepMinutes(request.sleepMinutes());
            return loadDay(memberId, date);
        });
    }

    public void deleteSleep(UUID memberId, LocalDate date) {
        validateDate(date);
        locked(memberId, memberSettings -> {
            days.findById(new WellnessDayId(memberId, date)).ifPresent(day -> day.setSleepMinutes(null));
            return null;
        });
    }

    public Day body(UUID memberId, LocalDate date, BodyRequest request) {
        validateDate(date);
        return locked(memberId, memberSettings -> {
            getOrCreateDay(memberId, date).setBody(request.weightKg().setScale(2), request.heightCm().setScale(2));
            return loadDay(memberId, date);
        });
    }

    public void deleteBody(UUID memberId, LocalDate date) {
        validateDate(date);
        locked(memberId, memberSettings -> {
            days.findById(new WellnessDayId(memberId, date)).ifPresent(day -> day.setBody(null, null));
            return null;
        });
    }

    private Day loadDay(UUID memberId, LocalDate date) {
        return loadDays(memberId, date, date).get(0);
    }

    private List<Day> loadDays(UUID memberId, LocalDate from, LocalDate to) {
        int goal = settings.findById(memberId).map(WellnessMemberSettings::getWaterGoalMl).orElse(DEFAULT_WATER_GOAL_ML);
        Map<LocalDate, WellnessDay> measurements = new HashMap<>();
        days.findAllByMemberIdAndDateBetween(memberId, from, to).forEach(row -> measurements.put(row.getDate(), row));
        Map<LocalDate, List<WaterEntry>> entriesByDate = new HashMap<>();
        for (var entry : water.findAllByMemberIdAndDateBetweenOrderByCreatedAtAscIdAsc(memberId, from, to)) {
            entriesByDate.computeIfAbsent(entry.getDate(), date -> new ArrayList<>())
                    .add(new WaterEntry(entry.getId(), entry.getAmountMl(), entry.getCreatedAt()));
        }

        LocalDate currentDate = today();
        List<Day> result = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            WellnessDay row = measurements.get(date);
            List<WaterEntry> entries = entriesByDate.getOrDefault(date, List.of());
            BigDecimal weight = row == null ? null : row.getWeightKg();
            BigDecimal height = row == null ? null : row.getHeightCm();
            BigDecimal bmi = weight == null || height == null ? null : weight.multiply(BigDecimal.valueOf(10000))
                    .divide(height.multiply(height), 1, RoundingMode.HALF_UP);
            result.add(new Day(date, currentDate, goal, entries.stream().mapToInt(WaterEntry::amountMl).sum(),
                    List.copyOf(entries), row == null ? null : row.getSleepMinutes(), weight, height, bmi));
        }
        return result;
    }

    private WellnessDay getOrCreateDay(UUID memberId, LocalDate date) {
        return days.findById(new WellnessDayId(memberId, date))
                .orElseGet(() -> days.save(new WellnessDay(memberId, date)));
    }

    private void validateDate(LocalDate date) {
        if (date == null || date.isBefore(EARLIEST_DATE) || date.isAfter(today())) {
            throw badRequest("Выберите дату с 1 января 2000 года по сегодняшний день по Москве.");
        }
    }

    private <T> T locked(UUID memberId, Function<WellnessMemberSettings, T> action) {
        ensureSettings(memberId);
        return transactions.execute(transaction -> {
            // Serialize diary writes per member so simultaneous updates preserve the daily limit.
            WellnessMemberSettings memberSettings = settings.findForUpdate(memberId).orElseThrow();
            return action.apply(memberSettings);
        });
    }

    private void ensureSettings(UUID memberId) {
        if (settings.existsById(memberId)) return;
        try {
            // Insert defaults in a separate transaction. A concurrent insert must not reset the saved goal.
            settings.insertDefaults(memberId);
        } catch (DataIntegrityViolationException exception) {
            if (!settings.existsById(memberId)) throw exception;
        }
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WELLNESS_INPUT", message);
    }
}
