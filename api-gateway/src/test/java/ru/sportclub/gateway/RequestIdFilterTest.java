package ru.sportclub.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {
    @Test
    void carriesTheSameRequestIdToServiceAndBrowser() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/club/system")
                .header("X-Request-ID", "gateway-check-1"));
        StepVerifier.create(new RequestIdFilter().filter(exchange, forwarded -> {
            assertThat(forwarded.getRequest().getHeaders().getFirst("X-Request-ID"))
                    .isEqualTo("gateway-check-1");
            return Mono.empty();
        })).verifyComplete();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-ID"))
                .isEqualTo("gateway-check-1");
    }

    @Test
    void replacesAnUntrustedRequestId() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/club/system")
                .header("X-Request-ID", "x".repeat(100)));
        StepVerifier.create(new RequestIdFilter().filter(exchange, forwarded -> {
            assertThat(forwarded.getRequest().getHeaders().getFirst("X-Request-ID"))
                    .matches("[a-f0-9-]{36}");
            return Mono.empty();
        })).verifyComplete();
    }
}

