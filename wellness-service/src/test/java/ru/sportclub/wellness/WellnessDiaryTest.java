package ru.sportclub.wellness;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.sportclub.foundation.TokenService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.sportclub.wellness.dto.WellnessModels.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "spring.config.location=classpath:/application-test.yml", "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "sportclub.service-title=Личные показатели",
        "sportclub.api-prefix=/api/wellness", "sportclub.configuration-source=test", "sportclub.brand.name=SportCLUB",
        "sportclub.brand.accent=#B5FF7A", "sportclub.brand.card-radius=24"
})
class WellnessDiaryTest {
    @DynamicPropertySource
    static void postgresSchema(DynamicPropertyRegistry properties) {
        String schema = "test_" + java.util.UUID.randomUUID().toString().replace("-", "");
        properties.add("sportclub.test.schema", () -> schema);
    }

    @Autowired TestRestTemplate http;
    @Autowired TokenService tokens;
    @Autowired JdbcTemplate jdbc;

    @Test
    void privateDiaryRequiresAuthenticationAndNewUserStartsWithoutPersonalData() {
        assertThat(http.getForEntity("/api/wellness/day", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String token = token();
        Day day = day(token, null);
        assertThat(day.date()).isEqualTo(today());
        assertThat(day.today()).isEqualTo(today());
        assertThat(day.waterGoalMl()).isEqualTo(2000);
        assertThat(day.waterTotalMl()).isZero();
        assertThat(day.waterEntries()).isEmpty();
        assertThat(day.sleepMinutes()).isNull();
        assertThat(day.weightKg()).isNull();
        assertThat(day.heightCm()).isNull();
        assertThat(day.bmi()).isNull();
    }

    @Test
    void waterEntriesAccumulateAndForeignAccountCannotSeeOrDeleteThem() {
        String owner = token(), other = token();
        LocalDate date = today().minusDays(1);
        Day one = post("/water", owner, new WaterRequest(date, 250), Day.class).getBody();
        Day two = post("/water", owner, new WaterRequest(date, 500), Day.class).getBody();
        assertThat(two.waterTotalMl()).isEqualTo(750);
        assertThat(two.waterEntries()).hasSize(2).allMatch(entry -> entry.createdAt() != null);
        assertThat(day(other, date).waterEntries()).isEmpty();
        UUID id = one.waterEntries().get(0).id();
        assertThat(delete("/water/" + id, other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete("/water/" + id, owner).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(day(owner, date).waterTotalMl()).isEqualTo(500);
        assertThat(delete("/water/" + id, owner).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(day(owner, today()).waterTotalMl()).isZero();
    }

    @Test
    void personalGoalIsIsolatedAndDoesNotEraseDailyEntries() {
        String owner = token(), other = token();
        post("/water", owner, new WaterRequest(today(), 250), Day.class);
        Day updated = put("/settings", owner, new SettingsRequest(3000), Day.class).getBody();
        assertThat(updated.waterGoalMl()).isEqualTo(3000);
        assertThat(updated.waterTotalMl()).isEqualTo(250);
        assertThat(day(owner, today().minusDays(1)).waterGoalMl()).isEqualTo(3000);
        assertThat(day(other, today()).waterGoalMl()).isEqualTo(2000);
    }

    @Test
    void bodySnapshotsAndSleepAreIndependentAndCanBeCorrectedOrDeleted() {
        String token = token(), other = token();
        LocalDate yesterday = today().minusDays(1);
        put("/days/" + yesterday + "/sleep", token, new SleepRequest(465), Day.class);
        Day first = put("/days/" + yesterday + "/body", token, body("180", "81"), Day.class).getBody();
        assertThat(first.sleepMinutes()).isEqualTo(465);
        assertThat(first.bmi()).isEqualByComparingTo("25.0");
        put("/days/" + today() + "/body", token, body("181.25", "80.25"), Day.class);
        Day past = day(token, yesterday);
        assertThat(past.heightCm()).isEqualByComparingTo("180");
        assertThat(past.weightKg()).isEqualByComparingTo("81");
        assertThat(past.bmi()).isEqualByComparingTo("25.0");
        assertThat(day(token, today()).bmi()).isEqualByComparingTo("24.4");
        assertThat(day(other, yesterday).sleepMinutes()).isNull();
        assertThat(day(other, yesterday).bmi()).isNull();
        put("/days/" + yesterday + "/body", token, body("180", "79.5"), Day.class);
        assertThat(day(token, yesterday).weightKg()).isEqualByComparingTo("79.5");
        delete("/days/" + yesterday + "/sleep", token);
        assertThat(day(token, yesterday).sleepMinutes()).isNull();
        assertThat(day(token, yesterday).bmi()).isNotNull();
        delete("/days/" + yesterday + "/body", token);
        assertThat(day(token, yesterday).bmi()).isNull();
        assertThat(day(token, yesterday).heightCm()).isNull();
        assertThat(day(token, today()).weightKg()).isEqualByComparingTo("80.25");
    }

    @Test
    void historyIncludesEmptyDatesInOrderAndDoesNotShareAnotherUsersData() {
        String token = token(), other = token();
        LocalDate from = today().minusDays(3);
        post("/water", token, new WaterRequest(from, 250), Day.class);
        put("/days/" + from.plusDays(1) + "/sleep", token, new SleepRequest(480), Day.class);
        put("/days/" + from.plusDays(2) + "/body", token, body("175", "70"), Day.class);
        Day[] history = get("/history?from=" + from + "&to=" + today(), token, Day[].class).getBody();
        assertThat(history).hasSize(4);
        assertThat(history).extracting(Day::date).containsExactly(from, from.plusDays(1), from.plusDays(2), today());
        assertThat(history[0].waterTotalMl()).isEqualTo(250);
        assertThat(history[1].sleepMinutes()).isEqualTo(480);
        assertThat(history[2].bmi()).isEqualByComparingTo("22.9");
        assertThat(history[3].waterTotalMl()).isZero();
        assertThat(history[3].sleepMinutes()).isNull();
        assertThat(history[3].bmi()).isNull();
        assertThat(get("/history?from=" + from + "&to=" + today(), other, Day[].class).getBody())
                .allMatch(day -> day.waterEntries().isEmpty() && day.sleepMinutes() == null && day.bmi() == null);
        assertThat(get("/history?from=" + today().minusDays(30) + "&to=" + today(), token, Day[].class).getBody()).hasSize(31);
    }

    @Test
    void rejectsInvalidDatesRangesAndBoundariesWithoutChangingSavedValues() {
        String token = token();
        assertBad(get("/day?date=" + today().plusDays(1), token, String.class));
        assertBad(get("/day?date=1999-12-31", token, String.class));
        assertBad(get("/day?date=wrong", token, String.class));
        assertBad(get("/history?from=" + today().minusDays(31) + "&to=" + today(), token, String.class));
        assertBad(get("/history?from=" + today() + "&to=" + today().minusDays(1), token, String.class));
        assertBad(post("/water", token, new WaterRequest(today(), 0), String.class));
        assertBad(post("/water", token, new WaterRequest(today(), 2001), String.class));
        assertBad(post("/water", token, Map.of("date", today(), "amountMl", 250.5), String.class));
        assertBad(post("/water", token, new WaterRequest(today().plusDays(1), 250), String.class));
        assertBad(put("/settings", token, new SettingsRequest(249), String.class));
        assertBad(put("/settings", token, new SettingsRequest(10001), String.class));
        assertBad(put("/days/" + today() + "/sleep", token, new SleepRequest(14), String.class));
        assertBad(put("/days/" + today() + "/sleep", token, new SleepRequest(1441), String.class));
        assertBad(put("/days/" + today() + "/sleep", token, Map.of("sleepMinutes", 480.5), String.class));
        assertBad(put("/days/" + today().plusDays(1) + "/sleep", token, new SleepRequest(480), String.class));
        assertBad(put("/days/" + today() + "/body", token, body("99", "70"), String.class));
        assertBad(put("/days/" + today() + "/body", token, body("180", "301"), String.class));
        assertBad(put("/days/" + today() + "/body", token, body("180.001", "70"), String.class));
        assertBad(put("/days/" + today() + "/body", token, Map.of("weightKg", 70), String.class));
        assertBad(delete("/days/1999-12-31/body", token));
        assertThat(day(token, today()).waterTotalMl()).isZero();
        assertThat(day(token, today()).sleepMinutes()).isNull();
        assertThat(day(token, today()).bmi()).isNull();
    }

    @Test
    void concurrentWaterAddsCannotExceedTheDailyLimit() throws Exception {
        String token = token();
        for (int index = 0; index < 4; index++) post("/water", token, new WaterRequest(today(), 2000), Day.class);
        post("/water", token, new WaterRequest(today(), 1000), Day.class);
        List<HttpStatusCode> statuses = race(
                () -> post("/water", token, new WaterRequest(today(), 750), String.class).getStatusCode(),
                () -> post("/water", token, new WaterRequest(today(), 750), String.class).getStatusCode());
        assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
        assertThat(day(token, today()).waterTotalMl()).isEqualTo(9750);
    }

    @Test
    void concurrentFirstDailyWritesPreserveBothSleepAndBody() throws Exception {
        UUID memberId = UUID.randomUUID();
        String token = tokens.issue(memberId, "MEMBER", "concurrent@test.local");
        List<HttpStatusCode> statuses = race(
                () -> put("/days/" + today() + "/sleep", token, new SleepRequest(480), String.class).getStatusCode(),
                () -> put("/days/" + today() + "/body", token, body("180", "81"), String.class).getStatusCode());
        assertThat(statuses).containsOnly(HttpStatus.OK);
        Day day = day(token, today());
        assertThat(day.sleepMinutes()).isEqualTo(480);
        assertThat(day.bmi()).isEqualByComparingTo("25.0");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wellness_days WHERE member_id = ?", Integer.class, memberId)).isEqualTo(1);
    }

    private List<HttpStatusCode> race(Callable<HttpStatusCode> first, Callable<HttpStatusCode> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        java.util.function.Function<Callable<HttpStatusCode>, Callable<HttpStatusCode>> task = action -> () -> {
            ready.countDown();
            if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("No start signal");
            return action.call();
        };
        try {
            var one = executor.submit(task.apply(first));
            var two = executor.submit(task.apply(second));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            return List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }

    private static BodyRequest body(String height, String weight) { return new BodyRequest(new BigDecimal(height), new BigDecimal(weight)); }
    private LocalDate today() { return LocalDate.now(ZoneId.of("Europe/Moscow")); }
    private String token() { UUID id = UUID.randomUUID(); return tokens.issue(id, "MEMBER", id + "@test.local"); }
    private Day day(String token, LocalDate date) { return get("/day" + (date == null ? "" : "?date=" + date), token, Day.class).getBody(); }
    private HttpEntity<Object> auth(String token, Object body) { var headers = new HttpHeaders(); headers.setBearerAuth(token); return new HttpEntity<>(body, headers); }
    private <T> ResponseEntity<T> get(String path, String token, Class<T> type) { return http.exchange("/api/wellness" + path, HttpMethod.GET, auth(token, null), type); }
    private <T> ResponseEntity<T> post(String path, String token, Object body, Class<T> type) { return http.exchange("/api/wellness" + path, HttpMethod.POST, auth(token, body), type); }
    private <T> ResponseEntity<T> put(String path, String token, Object body, Class<T> type) { return http.exchange("/api/wellness" + path, HttpMethod.PUT, auth(token, body), type); }
    private ResponseEntity<String> delete(String path, String token) { return http.exchange("/api/wellness" + path, HttpMethod.DELETE, auth(token, null), String.class); }
    private void assertBad(ResponseEntity<?> response) { assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST); }
}
