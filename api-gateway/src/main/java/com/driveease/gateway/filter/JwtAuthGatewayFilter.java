package com.driveease.gateway.filter;

import com.driveease.common.security.JwtService;
import com.driveease.common.security.JwtUserPrincipal;
import com.driveease.common.web.ApiError;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Validates JWT bearer tokens at the API Gateway edge and forwards verified user identity headers.
 */
@Component
public class JwtAuthGatewayFilter implements GlobalFilter, Ordered {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthGatewayFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        if (!path.startsWith("/api/") || HttpMethod.OPTIONS.equals(method) || isPublicEndpoint(path, method)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or malformed Authorization Bearer token");
        }

        String token = authHeader.substring(7).trim();
        try {
            JwtUserPrincipal principal = jwtService.parsePrincipal(token);
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", principal.userId() != null ? String.valueOf(principal.userId()) : "")
                    .header("X-User-Email", principal.email() != null ? principal.email() : "")
                    .header("X-User-Role", principal.role() != null ? principal.role() : "")
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (JwtException | IllegalArgumentException ex) {
            return unauthorized(exchange, "Invalid or expired JWT token");
        }
    }

    private boolean isPublicEndpoint(String path, HttpMethod method) {
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register")) {
            return true;
        }
        if (HttpMethod.GET.equals(method)) {
            if (path.startsWith("/api/vehicles") && !path.endsWith("/maintenance")) {
                return true;
            }
            if (path.equals("/api/bookings/availability")
                    || (path.startsWith("/api/bookings/vehicle/") && path.endsWith("/calendar"))) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiError body = ApiError.of(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                message,
                exchange.getRequest().getURI().getPath()
        );

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = ("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
