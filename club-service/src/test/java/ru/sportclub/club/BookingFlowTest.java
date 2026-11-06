package ru.sportclub.club;

import ru.sportclub.club.client.MembershipAccessClient;
import ru.sportclub.club.service.ClubService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.sportclub.foundation.ApiException;
import ru.sportclub.foundation.TokenService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=classpath:/application-test.yml",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "sportclub.service-title=Клуб и бронирования",
        "sportclub.api-prefix=/api/club",
        "sportclub.configuration-source=test",
        "sportclub.brand.name=SportCLUB",
        "sportclub.brand.accent=#B5FF7A",
        "sportclub.brand.card-radius=24",
        "sportclub.demo.enabled=false"
})
class BookingFlowTest {
    @DynamicPropertySource
    static void postgresSchema(DynamicPropertyRegistry properties) {
        String schema = "test_" + java.util.UUID.randomUUID().toString().replace("-", "");
        properties.add("sportclub.test.schema", () -> schema);
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenService tokens;
    @MockitoBean MembershipAccessClient membership;
    UUID trainerId;
    UUID classId;
    UUID member;
    String token;

    @BeforeEach
    void fixture() {
        reset(membership);
        jdbc.update("DELETE FROM bookings");
        jdbc.update("DELETE FROM club_classes");
        jdbc.update("DELETE FROM trainers");
        trainerId = UUID.randomUUID();
        classId = UUID.randomUUID();
        member = UUID.randomUUID();
        token = tokens.issue(member, "MEMBER", "member@test.local");
        jdbc.update("INSERT INTO trainers (id, name, specialty, bio, initials) VALUES (?, 'Тестовый тренер', 'Силовые', 'Описание', 'ТТ')", trainerId);
        var start = LocalDate.now(ClubService.MOSCOW).plusDays(1).atTime(19, 0).atZone(ClubService.MOSCOW).toOffsetDateTime();
        jdbc.update("INSERT INTO club_classes (id, title, class_type, trainer_id, room, starts_at, ends_at, capacity) VALUES (?, 'Тестовое занятие', 'STRENGTH', ?, 'Зал 1', ?, ?, 2)",
                classId, trainerId, start, start.plusMinutes(50));
    }

    @Test
    void publicScheduleWorksButBookingRequiresAuthentication() {
        var date = LocalDate.now(ClubService.MOSCOW).plusDays(1);
        var schedule = http.getForEntity("/api/club/classes?date=" + date, List.class);
        assertThat(schedule.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(schedule.getBody()).hasSize(1);
        assertThat(http.postForEntity("/api/club/bookings", Map.of("classId", classId), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void duplicateBookingIsRejectedAndCapacityReflectsTheBooking() {
        var created = post(token);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(post(token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var schedule = http.exchange("/api/club/classes?date=" + LocalDate.now(ClubService.MOSCOW).plusDays(1),
                HttpMethod.GET, auth(token, null), List.class);
        Map<?, ?> item = (Map<?, ?>) schedule.getBody().get(0);
        assertThat(item.get("availableSpots")).isEqualTo(1);
        assertThat(item.get("bookedByMe")).isEqualTo(true);
        var bookings = http.exchange("/api/club/me/bookings", HttpMethod.GET, auth(token, null), List.class);
        assertThat(bookings.getBody()).hasSize(1);
    }

    @Test
    void cancellationIsOwnerOnlyIdempotentAndAllowsRebooking() {
        var created = post(token);
        var id = created.getBody().get("id");
        String otherToken = tokens.issue(UUID.randomUUID(), "MEMBER", "other@test.local");
        assertThat(http.exchange("/api/club/bookings/" + id, HttpMethod.DELETE, auth(otherToken, null), Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.exchange("/api/club/bookings/" + id, HttpMethod.DELETE, auth(token, null), Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(http.exchange("/api/club/bookings/" + id, HttpMethod.DELETE, auth(token, null), Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(post(token).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE status = 'CONFIRMED'", Integer.class)).isEqualTo(1);
    }

    @Test
    void inactiveMembershipDoesNotReserveAPlace() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "MEMBERSHIP_REQUIRED", "Нужен абонемент"))
                .when(membership).requireActiveMembership(any(), anyString());
        assertThat(post(token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings", Integer.class)).isZero();
    }

    @Test
    void unavailableMembershipCheckDoesNotReserveAPlace() {
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MEMBERSHIP_CHECK_UNAVAILABLE", "Недоступно"))
                .when(membership).requireActiveMembership(any(), anyString());
        assertThat(post(token).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings", Integer.class)).isZero();
    }

    @Test
    void pastClassCannotBeBooked() {
        OffsetDateTime start = OffsetDateTime.now().minusHours(2);
        jdbc.update("UPDATE club_classes SET starts_at = ?, ends_at = ? WHERE id = ?", start, start.plusMinutes(50), classId);
        assertThat(post(token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void onlyOneOfTwoAuthenticatedConcurrentRequestsGetsTheLastPlace() throws Exception {
        jdbc.update("UPDATE club_classes SET capacity = 1 WHERE id = ?", classId);
        var barrier = new CyclicBarrier(2);
        doAnswer(invocation -> { barrier.await(5, TimeUnit.SECONDS); return null; })
                .when(membership).requireActiveMembership(any(), anyString());
        String secondToken = tokens.issue(UUID.randomUUID(), "MEMBER", "second@test.local");
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> post(token).getStatusCode());
            var second = executor.submit(() -> post(secondToken).getStatusCode());
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE status = 'CONFIRMED'", Integer.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private org.springframework.http.ResponseEntity<Map> post(String bearer) {
        return http.exchange("/api/club/bookings", HttpMethod.POST, auth(bearer, Map.of("classId", classId)), Map.class);
    }

    private HttpEntity<?> auth(String bearer, Object body) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(bearer);
        return new HttpEntity<>(body, headers);
    }
}
