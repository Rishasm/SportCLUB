package ru.sportclub.foundation;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
@Import({SecurityConfiguration.class, ApiErrorHandler.class})
public class FoundationConfiguration {
    @Bean
    SystemController systemController(JdbcTemplate jdbc, DataSource dataSource, Environment environment) {
        return new SystemController(jdbc, dataSource, environment);
    }

    @Bean
    RequestLoggingFilter requestLoggingFilter() {
        return new RequestLoggingFilter();
    }

    @Bean
    OpenAPI serviceOpenApi(@Value("${sportclub.service-title}") String title) {
        return new OpenAPI().info(new Info().title("SportCLUB · " + title)
                .version("0.2.0")
                .description("Учебный API SportCLUB. Личные операции требуют Bearer JWT."))
                .components(new io.swagger.v3.oas.models.Components().addSecuritySchemes("bearerAuth",
                        new io.swagger.v3.oas.models.security.SecurityScheme()
                                .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")));
    }
}

