package ru.sportclub.foundation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RequestLoggingFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID = "X-Request-ID";
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(REQUEST_ID);
        String requestId = incoming != null && incoming.matches("[A-Za-z0-9._-]{1,64}")
                ? incoming : UUID.randomUUID().toString();
        long start = System.nanoTime();
        response.setHeader(REQUEST_ID, requestId);
        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
            try {
                filterChain.doFilter(request, response);
            } finally {
                log.info("method={} path={} status={} durationMs={}", request.getMethod(),
                        request.getRequestURI(), response.getStatus(),
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            }
        }
    }
}

