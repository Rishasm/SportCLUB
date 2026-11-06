package ru.sportclub.foundation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean
    SecretKey tokenKey(@Value("${sportclub.security.jwt-secret:sportclub-local-development-key-change-before-deploy-2026}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalArgumentException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("sportclub"));
        return decoder;
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }

    @Bean
    TokenService tokenService(JwtEncoder encoder) { return new TokenService(encoder); }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(10); }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ObjectMapper json) throws Exception {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        org.springframework.security.web.AuthenticationEntryPoint unauthorized = (request, response, error) -> {
            response.setHeader("WWW-Authenticate", "Bearer");
            writeError(json, request, response, 401, "AUTH_REQUIRED", "Войдите в аккаунт, чтобы продолжить.");
        };
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/*/system", "/api/*/v3/api-docs", "/api/*/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/error",
                                "/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/metrics/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/members/auth/register", "/api/members/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/members/plans", "/api/club/classes", "/api/club/classes/*",
                                "/api/club/trainers", "/api/club/calendar").permitAll()
                        .requestMatchers("/api/club/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/club/trainer/**").hasRole("TRAINER")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(unauthorized))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler((request, response, error) -> writeError(json, request, response,
                                403, "ACCESS_DENIED", "Недостаточно прав для этого действия.")))
                .build();
    }

    private static void writeError(ObjectMapper json, HttpServletRequest request, HttpServletResponse response,
                                   int status, String code, String message) throws IOException {
        String requestId = ApiErrorHandler.requestId(request);
        response.setStatus(status);
        response.setHeader("X-Request-ID", requestId);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(), Map.of("code", code, "message", message, "requestId", requestId));
    }
}

