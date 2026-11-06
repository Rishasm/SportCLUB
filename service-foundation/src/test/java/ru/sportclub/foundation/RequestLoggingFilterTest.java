package ru.sportclub.foundation;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {
    @Test
    void preservesSafeRequestIdAndClearsLoggingContext() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/members/system");
        request.addHeader("X-Request-ID", "demo-request-42");
        var response = new MockHttpServletResponse();
        var seen = new AtomicReference<String>();
        new RequestLoggingFilter().doFilter(request, response,
                (req, res) -> seen.set(org.slf4j.MDC.get("requestId")));
        assertThat(response.getHeader("X-Request-ID")).isEqualTo("demo-request-42");
        assertThat(seen.get()).isEqualTo("demo-request-42");
        assertThat(org.slf4j.MDC.get("requestId")).isNull();
    }

    @Test
    void replacesUnsafeIncomingRequestId() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "untrusted\r\nlog-line");
        var response = new MockHttpServletResponse();
        new RequestLoggingFilter().doFilter(request, response, (req, res) -> {});
        assertThat(response.getHeader("X-Request-ID")).matches("[a-f0-9-]{36}");
    }
}

