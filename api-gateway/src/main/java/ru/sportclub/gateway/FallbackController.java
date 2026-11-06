package ru.sportclub.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.util.Map;

@RestController
public class FallbackController {
    @RequestMapping("/fallback/{service}")
    public ResponseEntity<Map<String, String>> unavailable(@PathVariable String service,
                                                         ServerWebExchange exchange) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "code", "SERVICE_UNAVAILABLE",
                "service", service,
                "message", "Сервис временно недоступен. Повторите запрос позже.",
                "requestId", exchange.getRequest().getHeaders().getFirst("X-Request-ID") == null
                        ? "" : exchange.getRequest().getHeaders().getFirst("X-Request-ID")));
    }
}

