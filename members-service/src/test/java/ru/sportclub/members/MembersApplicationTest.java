package ru.sportclub.members;

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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static ru.sportclub.members.dto.MembersDtos.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=classpath:/application-test.yml",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "sportclub.service-title=Клиенты и абонементы",
        "sportclub.api-prefix=/api/members",
        "sportclub.configuration-source=test",
        "sportclub.brand.name=SportCLUB",
        "sportclub.brand.accent=#B5FF7A",
        "sportclub.brand.card-radius=24"
})
class MembersApplicationTest {
    @DynamicPropertySource
    static void postgresSchema(DynamicPropertyRegistry properties) {
        String schema = "test_" + UUID.randomUUID().toString().replace("-", "");
        properties.add("sportclub.test.schema", () -> schema);
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void appliesMigrationAndReportsItsOwnDatabase() {
        var response = http.getForEntity("/api/members/system", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("service")).isEqualTo("members-service");
        Map<?, ?> database = (Map<?, ?>) body.get("database");
        assertThat(database.get("engine")).isEqualTo("PostgreSQL");
        assertThat(database.get("serviceCode")).isEqualTo("members-service");
        assertThat(database.get("schemaVersion")).isEqualTo(3);
        assertThat(response.getHeaders().getFirst("X-Request-ID")).isNotBlank();
    }

    @Test
    void registersNormalizesEmailAndStoresOnlyPasswordHash() {
        String email = "New-" + UUID.randomUUID() + "@SPORTCLUB.LOCAL";
        var response = http.postForEntity("/api/members/auth/register",
                new RegisterRequest("  Анна  ", email, "MemberPass123!"), AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AuthResponse auth = response.getBody();
        assertThat(auth).isNotNull();
        assertThat(auth.token()).isNotBlank();
        assertThat(auth.expiresIn()).isEqualTo(7200);
        assertThat(auth.user().name()).isEqualTo("Анна");
        assertThat(auth.user().email()).isEqualTo(email.toLowerCase(java.util.Locale.ROOT));
        assertThat(auth.user().role()).isEqualTo("MEMBER");
        String stored = jdbc.queryForObject("SELECT password_hash FROM member_accounts WHERE id = ?",
                String.class, auth.user().id());
        assertThat(stored).startsWith("$2").isNotEqualTo("MemberPass123!");
        assertThat(profile(auth.token()).getBody().id()).isEqualTo(auth.user().id());
    }

    @Test
    void rejectsCaseInsensitiveDuplicateEmail() {
        AuthResponse auth = register();
        var response = http.postForEntity("/api/members/auth/register",
                new RegisterRequest("Другой клиент", auth.user().email().toUpperCase(java.util.Locale.ROOT),
                        "MemberPass123!"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("EMAIL_TAKEN");
    }

    @Test
    void logsInAndRejectsIncorrectCredentialsWithoutLeakingAccountExistence() {
        AuthResponse auth = register();
        var login = http.postForEntity("/api/members/auth/login",
                new LoginRequest(auth.user().email(), "MemberPass123!"), AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody().user().id()).isEqualTo(auth.user().id());
        var wrongPassword = http.postForEntity("/api/members/auth/login",
                new LoginRequest(auth.user().email(), "Incorrect123!"), Map.class);
        var missingAccount = http.postForEntity("/api/members/auth/login",
                new LoginRequest(UUID.randomUUID() + "@sportclub.local", "Incorrect123!"), Map.class);
        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(missingAccount.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getBody().get("code")).isEqualTo("INVALID_CREDENTIALS");
        assertThat(missingAccount.getBody().get("code")).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void requiresAuthenticationAndKeepsProfilesSeparate() {
        AuthResponse first = register();
        AuthResponse second = register();
        assertThat(http.getForEntity("/api/members/me", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        Profile own = profile(first.token()).getBody();
        assertThat(own.id()).isEqualTo(first.user().id()).isNotEqualTo(second.user().id());
        assertThat(own.membership()).isNull();
        // Supplying another member id cannot change the authenticated identity.
        var injected = http.exchange("/api/members/me?memberId=" + second.user().id(), HttpMethod.GET,
                authorized(first.token(), null), Profile.class);
        assertThat(injected.getBody().id()).isEqualTo(first.user().id());
        assertThat(http.exchange("/api/members/me", HttpMethod.GET,
                authorized("not-a-jwt", null), Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void activatesOneMembershipAndRejectsDuplicateActivation() {
        AuthResponse auth = register();
        var plans = http.getForEntity("/api/members/plans", Plan[].class);
        assertThat(plans.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(plans.getBody()).hasSize(3);
        var activated = activate(auth.token(), "rhythm");
        assertThat(activated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Membership membership = activated.getBody();
        assertThat(membership.status()).isEqualTo("ACTIVE");
        assertThat(membership.planId()).isEqualTo("rhythm");
        assertThat(membership.validUntil()).isAfter(membership.startsAt());
        assertThat(profile(auth.token()).getBody().membership().id()).isEqualTo(membership.id());
        var access = access(auth.token());
        assertThat(access.memberId()).isEqualTo(auth.user().id());
        assertThat(access.active()).isTrue();
        var duplicate = http.exchange("/api/members/me/membership", HttpMethod.POST,
                authorized(auth.token(), new ActivateMembershipRequest("focus")), Map.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().get("code")).isEqualTo("MEMBERSHIP_ACTIVE");
    }

    @Test
    void deniesExpiredMembershipAndAllowsRenewalWithoutChangingAnotherUser() {
        AuthResponse auth = register();
        AuthResponse other = register();
        Membership initial = activate(auth.token(), "start").getBody();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("UPDATE member_memberships SET starts_at = ?, valid_until = ? WHERE id = ?",
                now.minusDays(32), now.minusDays(2), initial.id());
        assertThat(access(auth.token()).active()).isFalse();
        assertThat(profile(auth.token()).getBody().membership().status()).isEqualTo("EXPIRED");
        assertThat(access(other.token()).active()).isFalse();
        Membership renewed = activate(auth.token(), "rhythm").getBody();
        assertThat(renewed.id()).isNotEqualTo(initial.id());
        assertThat(access(auth.token()).active()).isTrue();
        assertThat(access(other.token()).active()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_memberships WHERE member_id = ?",
                Integer.class, auth.user().id())).isEqualTo(2);
    }

    @Test
    void serializesConcurrentActivationForOneMember() throws Exception {
        AuthResponse auth = register();
        CountDownLatch start = new CountDownLatch(1);
        var first = CompletableFuture.supplyAsync(() -> activateAfter(start, auth.token()));
        var second = CompletableFuture.supplyAsync(() -> activateAfter(start, auth.token()));
        start.countDown();
        assertThat(java.util.List.of(first.get(), second.get()))
                .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_memberships WHERE member_id = ?",
                Integer.class, auth.user().id())).isEqualTo(1);
    }

    @Test
    void rejectsInvalidRegistrationAndUnknownPlans() {
        var invalid = http.postForEntity("/api/members/auth/register",
                new RegisterRequest("А", "invalid", "short"), Map.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        AuthResponse auth = register();
        var unknown = http.exchange("/api/members/me/membership", HttpMethod.POST,
                authorized(auth.token(), new ActivateMembershipRequest("does-not-exist")), Map.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody().get("code")).isEqualTo("PLAN_NOT_FOUND");
    }

    private AuthResponse register() {
        var response = http.postForEntity("/api/members/auth/register",
                new RegisterRequest("Тестовый клиент", UUID.randomUUID() + "@sportclub.local", "MemberPass123!"),
                AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private ResponseEntity<Profile> profile(String token) {
        return http.exchange("/api/members/me", HttpMethod.GET, authorized(token, null), Profile.class);
    }

    private Access access(String token) {
        var response = http.exchange("/api/members/me/access", HttpMethod.GET, authorized(token, null), Access.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<Membership> activate(String token, String plan) {
        return http.exchange("/api/members/me/membership", HttpMethod.POST,
                authorized(token, new ActivateMembershipRequest(plan)), Membership.class);
    }

    private HttpStatus activateAfter(CountDownLatch latch, String token) {
        try {
            latch.await();
            var response = http.exchange("/api/members/me/membership", HttpMethod.POST,
                    authorized(token, new ActivateMembershipRequest("rhythm")), Map.class);
            return HttpStatus.valueOf(response.getStatusCode().value());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private HttpEntity<?> authorized(String token, Object body) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }
}
