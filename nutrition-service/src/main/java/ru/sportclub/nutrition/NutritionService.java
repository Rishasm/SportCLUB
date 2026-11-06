package ru.sportclub.nutrition;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.sportclub.foundation.ApiException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import static ru.sportclub.nutrition.NutritionModels.*;

@Service
public class NutritionService {
    private final JdbcTemplate jdbc;

    public NutritionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Food> foods() {
        return jdbc.query("select id as food_id, name, kcal, protein, fat, carbs from foods order by name",
                (row, index) -> food(row));
    }

    public Day day(UUID member, LocalDate date) {
        checkDate(date);
        var entries = jdbc.query("""
                select e.id, e.meal, e.grams, e.food_id, f.name, f.kcal, f.protein, f.fat, f.carbs
                from nutrition_entries e join foods f on f.id = e.food_id
                where e.member_id = ? and e.entry_date = ? order by e.created_at, e.id
                """, (row, index) -> {
            Food food = food(row);
            int grams = row.getInt("grams");
            return new Entry(row.getObject("id", UUID.class), Meal.valueOf(row.getString("meal")),
                    food, grams, food.per100g().portion(grams));
        }, member, date);
        var meals = new EnumMap<Meal, Nutrients>(Meal.class);
        for (Entry entry : entries) meals.merge(entry.meal(), entry.portion(), Nutrients::plus);
        Nutrients total = meals.values().stream().reduce(Nutrients.ZERO, Nutrients::plus);
        return new Day(date, entries, meals, total);
    }

    public UUID add(UUID member, EntryRequest request) {
        checkDate(request.date());
        UUID id = UUID.randomUUID();
        int count = jdbc.update("""
                insert into nutrition_entries (id, member_id, entry_date, meal, food_id, grams)
                select ?, ?, ?, ?, id, ? from foods where id = ?
                """, id, member, request.date(), request.meal().name(), request.grams(), request.foodId());
        if (count == 0) throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_FOOD", "Выбери продукт из справочника.");
        return id;
    }

    public void update(UUID member, UUID id, EntryRequest request) {
        checkDate(request.date());
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from foods where id = ?)",
                Boolean.class, request.foodId()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_FOOD", "Выбери продукт из справочника.");
        }
        requireChanged(jdbc.update("""
                update nutrition_entries set entry_date = ?, meal = ?, food_id = ?, grams = ?
                where id = ? and member_id = ?
                """, request.date(), request.meal().name(), request.foodId(), request.grams(), id, member));
    }

    public void delete(UUID member, UUID id) {
        requireChanged(jdbc.update("delete from nutrition_entries where id = ? and member_id = ?", id, member));
    }

    private static void requireChanged(int count) {
        if (count == 0) throw new ApiException(HttpStatus.NOT_FOUND, "ENTRY_NOT_FOUND", "Запись не найдена.");
    }

    private static void checkDate(LocalDate date) {
        if (date.isBefore(LocalDate.of(2000, 1, 1)) || date.isAfter(LocalDate.now(ZoneId.of("Europe/Moscow")))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE", "Выбери сегодняшнюю или прошедшую дату.");
        }
    }

    private static Food food(ResultSet row) throws SQLException {
        return new Food(row.getString("food_id"), row.getString("name"), new Nutrients(
                row.getBigDecimal("kcal"), row.getBigDecimal("protein"), row.getBigDecimal("fat"), row.getBigDecimal("carbs")));
    }
}
