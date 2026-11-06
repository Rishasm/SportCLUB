package ru.sportclub.wellness;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=classpath:/application-test.yml",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "sportclub.service-title=Личные показатели",
        "sportclub.api-prefix=/api/wellness",
        "sportclub.configuration-source=test",
        "sportclub.brand.name=SportCLUB",
        "sportclub.brand.accent=#B5FF7A",
        "sportclub.brand.card-radius=24"
})
class WellnessApplicationTest {
    @DynamicPropertySource
    static void postgresSchema(DynamicPropertyRegistry properties) {
        String schema = "test_" + java.util.UUID.randomUUID().toString().replace("-", "");
        properties.add("sportclub.test.schema", () -> schema);
    }

    @Autowired
    TestRestTemplate http;

    @Test
    void appliesMigrationAndReportsItsOwnDatabase() {
        var response = http.getForEntity("/api/wellness/system", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("service")).isEqualTo("wellness-service");
        Map<?, ?> database = (Map<?, ?>) body.get("database");
        assertThat(database.get("engine")).isEqualTo("PostgreSQL");
        assertThat(database.get("serviceCode")).isEqualTo("wellness-service");
        assertThat(database.get("schemaVersion")).isEqualTo(2);
        assertThat(response.getHeaders().getFirst("X-Request-ID")).isNotBlank();
    }
}
