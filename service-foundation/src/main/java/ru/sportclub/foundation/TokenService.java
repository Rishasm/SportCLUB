package ru.sportclub.foundation;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class TokenService {
    public static final long EXPIRES_IN = 7200;
    private final JwtEncoder encoder;

    public TokenService(JwtEncoder encoder) { this.encoder = encoder; }

    public String issue(UUID memberId, String role, String email) {
        return issue(memberId, role, email, null);
    }

    public String issue(UUID memberId, String role, String email, String name) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("sportclub").subject(memberId.toString())
                .issuedAt(now).expiresAt(now.plusSeconds(EXPIRES_IN))
                .claim("roles", List.of(role)).claim("email", email);
        if (name != null) claims.claim("name", name);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }
}

