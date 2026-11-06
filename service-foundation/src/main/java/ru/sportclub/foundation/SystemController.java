package ru.sportclub.foundation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("${sportclub.api-prefix}/system")
@Tag(name = "Инфраструктура")
public class SystemController {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final Environment environment;

    public SystemController(JdbcTemplate jdbc, DataSource dataSource, Environment environment) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.environment = environment;
    }

    @GetMapping
    @Operation(summary = "Проверить централизованную конфигурацию и собственную базу сервиса")
    public Map<String, Object> status(@RequestHeader(value = "X-Request-ID", defaultValue = "") String requestId)
            throws SQLException {
        Map<String, Object> identity = jdbc.queryForMap(
                "select service_code, schema_version from service_identity where id = 1");
        String migration = jdbc.queryForObject(
                "select version from flyway_schema_history where success = true order by installed_rank desc limit 1",
                String.class);
        String database;
        try (var connection = dataSource.getConnection()) {
            database = connection.getMetaData().getDatabaseProductName();
        }
        return Map.of(
                "service", environment.getRequiredProperty("spring.application.name"),
                "title", environment.getRequiredProperty("sportclub.service-title"),
                "status", "UP",
                "brand", Map.of("name", environment.getRequiredProperty("sportclub.brand.name"),
                        "accent", environment.getRequiredProperty("sportclub.brand.accent"),
                        "cardRadius", environment.getRequiredProperty("sportclub.brand.card-radius")),
                "configurationSource", environment.getRequiredProperty("sportclub.configuration-source"),
                "database", Map.of("engine", database, "serviceCode", identity.get("service_code"),
                        "schemaVersion", Integer.parseInt(migration)),
                "requestId", requestId,
                "timestamp", Instant.now().toString());
    }
}

