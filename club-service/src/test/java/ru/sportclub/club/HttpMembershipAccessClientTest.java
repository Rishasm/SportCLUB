package ru.sportclub.club;

import ru.sportclub.club.client.HttpMembershipAccessClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.sportclub.foundation.ApiException;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpMembershipAccessClientTest {
    MockRestServiceServer server;
    HttpMembershipAccessClient client;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpMembershipAccessClient(builder);
    }

    @Test
    void propagatesBearerAndAcceptsOnlyTheCurrentMembersActiveAccess() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo("http://members-service/api/members/me/access"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer token-1"))
                .andRespond(withSuccess(access(id, true, OffsetDateTime.now().plusDays(1)), MediaType.APPLICATION_JSON));
        client.requireActiveMembership(id, "token-1");
        server.verify();
    }

    @Test
    void rejectsAccessDataForADifferentMember() {
        server.expect(requestTo("http://members-service/api/members/me/access"))
                .andRespond(withSuccess(access(UUID.randomUUID(), true, OffsetDateTime.now().plusDays(1)), MediaType.APPLICATION_JSON));
        var failure = catchThrowableOfType(() -> client.requireActiveMembership(UUID.randomUUID(), "token"), ApiException.class);
        assertThat(failure.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(failure.getCode()).isEqualTo("MEMBERSHIP_CHECK_UNAVAILABLE");
    }

    @Test
    void rejectsExpiredAccessEvenIfUpstreamActiveFlagIsStale() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo("http://members-service/api/members/me/access"))
                .andRespond(withSuccess(access(id, true, OffsetDateTime.now().minusSeconds(1)), MediaType.APPLICATION_JSON));
        var failure = catchThrowableOfType(() -> client.requireActiveMembership(id, "token"), ApiException.class);
        assertThat(failure.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(failure.getCode()).isEqualTo("MEMBERSHIP_REQUIRED");
    }

    @Test
    void upstreamFailureCannotProduceSuccessfulAccess() {
        server.expect(requestTo("http://members-service/api/members/me/access")).andRespond(withServerError());
        var failure = catchThrowableOfType(() -> client.requireActiveMembership(UUID.randomUUID(), "token"), ApiException.class);
        assertThat(failure.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private String access(UUID id, boolean active, OffsetDateTime until) {
        return "{\"memberId\":\"" + id + "\",\"active\":" + active + ",\"validUntil\":\"" + until + "\"}";
    }
}
