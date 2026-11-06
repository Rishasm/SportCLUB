package ru.sportclub.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class RequestIdFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst("X-Request-ID");
        String requestId = incoming != null && incoming.matches("[A-Za-z0-9._-]{1,64}")
                ? incoming : UUID.randomUUID().toString();
        ServerWebExchange forwarded = exchange.mutate().request(request ->
                request.headers(headers -> headers.set("X-Request-ID", requestId))).build();
        forwarded.getResponse().getHeaders().set("X-Request-ID", requestId);
        long start = System.nanoTime();
        return chain.filter(forwarded).doFinally(signal -> log.info(
                "requestId={} method={} path={} status={} durationMs={}", requestId,
                exchange.getRequest().getMethod(), exchange.getRequest().getPath(),
                exchange.getResponse().getStatusCode(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}

