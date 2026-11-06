package ru.sportclub.club;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import ru.sportclub.foundation.TokenService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=classpath:/application-test.yml",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "sportclub.service-title=Клуб и бронирования",
        "sportclub.api-prefix=/api/club",
        "sportclub.configuration-source=test",
        "sportclub.brand.name=SportCLUB",
        "sportclub.brand.accent=#B5FF7A",
        "sportclub.brand.card-radius=24"
})
class ClubApplicationTest {
    @DynamicPropertySource
    static void postgresSchema(DynamicPropertyRegistry properties) {
        String schema = "test_" + java.util.UUID.randomUUID().toString().replace("-", "");
        properties.add("sportclub.test.schema", () -> schema);
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    TokenService tokens;

    @Test
    void appliesMigrationAndReportsItsOwnDatabase() {
        var response = http.getForEntity("/api/club/system", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("service")).isEqualTo("club-service");
        Map<?, ?> database = (Map<?, ?>) body.get("database");
        assertThat(database.get("engine")).isEqualTo("PostgreSQL");
        assertThat(database.get("serviceCode")).isEqualTo("club-service");
        assertThat(database.get("schemaVersion")).isEqualTo(3);
        assertThat(response.getHeaders().getFirst("X-Request-ID")).isNotBlank();
    }

    @Test
    void onlyAnAdminCanPublishAnAnnouncementThatClientsReceive() {
        var message = Map.of("title", "Часы работы", "body", "Клуб откроется в девять.");
        var client = new HttpHeaders();
        client.setBearerAuth(tokens.issue(UUID.randomUUID(), "MEMBER", "client@example.test"));
        var admin = new HttpHeaders();
        admin.setBearerAuth(tokens.issue(UUID.randomUUID(), "ADMIN", "admin@example.test"));
        assertThat(http.postForEntity("/api/club/admin/announcements", new HttpEntity<>(message, client), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var published = http.postForEntity("/api/club/admin/announcements", new HttpEntity<>(message, admin), Map.class);
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var notices = http.exchange("/api/club/me/notices", HttpMethod.GET, new HttpEntity<>(client), List.class);
        assertThat(notices.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(notices.getBody()).hasSize(1);
        Map<?, ?> received = (Map<?, ?>) notices.getBody().get(0);
        assertThat(received.get("id")).isEqualTo(published.getBody().get("id"));
        assertThat(received.get("body")).isEqualTo(message.get("body"));
        assertThat(received.get("announcement")).isEqualTo(true);
    }
}
