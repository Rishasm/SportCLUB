package ru.sportclub.foundation;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class TokenServiceTest {
    @Test
    void acceptsOnlyTokensSignedByTheConfiguredKey() {
        var config = new SecurityConfiguration();
        var key = config.tokenKey("test-sportclub-signing-key-with-32-bytes");
        var decoder = config.jwtDecoder(key);
        var subject = UUID.randomUUID();
        var token = new TokenService(config.jwtEncoder(key)).issue(subject, "MEMBER", "test@example.org");
        assertThat(decoder.decode(token).getSubject()).isEqualTo(subject.toString());
        assertThat(decoder.decode(token).getClaimAsStringList("roles")).containsExactly("MEMBER");
        var otherKey = config.tokenKey("other-sportclub-signing-key-with-32-bytes");
        assertThatThrownBy(() -> config.jwtDecoder(otherKey).decode(token)).isInstanceOf(JwtException.class);
    }
}

