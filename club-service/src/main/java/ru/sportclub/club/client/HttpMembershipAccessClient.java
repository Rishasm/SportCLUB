package ru.sportclub.club.client;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.sportclub.foundation.ApiException;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class HttpMembershipAccessClient implements MembershipAccessClient {
    private final RestClient client;

    public HttpMembershipAccessClient(@Qualifier("membershipRestClientBuilder") RestClient.Builder builder) {
        client = builder.baseUrl("http://members-service").build();
    }

    @Override
    public void requireActiveMembership(UUID memberId, String bearerToken) {
        AccessResponse access;
        try {
            String requestId = MDC.get("requestId");
            access = client.get().uri("/api/members/me/access")
                    .header("Authorization", "Bearer " + bearerToken)
                    .header("X-Request-ID", requestId == null ? UUID.randomUUID().toString() : requestId)
                    .retrieve().body(AccessResponse.class);
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MEMBERSHIP_CHECK_UNAVAILABLE",
                    "Не удалось проверить абонемент. Попробуйте записаться немного позже.");
        }
        if (access == null || !memberId.equals(access.memberId())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MEMBERSHIP_CHECK_UNAVAILABLE",
                    "Не удалось проверить абонемент. Попробуйте записаться немного позже.");
        }
        if (!access.active() || access.validUntil() == null || !access.validUntil().isAfter(OffsetDateTime.now())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MEMBERSHIP_REQUIRED",
                    "Для записи нужен действующий абонемент.");
        }
    }

    public record AccessResponse(UUID memberId, boolean active, OffsetDateTime validUntil) {}
}
