package com.example.apigateway.filter;

import com.example.apigateway.dto.ApiResponse;
import com.example.util.JwtUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Component
public class AuthenticationFilter
        extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private final RouteValidator validator;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public AuthenticationFilter(
            RouteValidator validator,
            JwtUtil jwtUtil,
            ObjectMapper objectMapper
    ) {
        super(Config.class);
        this.validator = validator;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    public GatewayFilter apply(Config config) {

        return (exchange, chain) -> {

            ServerHttpRequest request = exchange.getRequest();

            ServerHttpRequest sanitizedRequest = request.mutate()
                    .headers(headers -> {
                        headers.remove("X-User-Id");
                        headers.remove("X-User-Email");
                        headers.remove("X-User-Role");
                    })
                    .build();

            exchange = exchange
                    .mutate()
                    .request(sanitizedRequest)
                    .build();

            request = exchange.getRequest();

            if (!validator.isSecured.test(request)) {
                return chain.filter(exchange);
            }

            String authHeader = request.getHeaders()
                    .getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || authHeader.isBlank()) {

                log.warn(
                        "Authentication failed: Missing Authorization header for path: {}",
                        request.getURI().getPath()
                );

                return onError(
                        exchange,
                        "Authorization header is missing",
                        HttpStatus.UNAUTHORIZED
                );
            }

            if (!authHeader.startsWith("Bearer ")) {

                log.warn(
                        "Authentication failed: Invalid Authorization header format for path: {}",
                        request.getURI().getPath()
                );

                return onError(
                        exchange,
                        "Invalid Authorization header format",
                        HttpStatus.UNAUTHORIZED
                );
            }

            String token = authHeader.substring(7).trim();

            if (token.isBlank()) {

                return onError(
                        exchange,
                        "JWT token is missing",
                        HttpStatus.UNAUTHORIZED
                );
            }

            try {

                String email = jwtUtil.extractEmail(token);
                Long id = jwtUtil.extractId(token);
                String role = jwtUtil.extractRole(token);

                if (email == null || email.isBlank()) {

                    log.warn(
                            "Authentication failed: Email not found in JWT for path: {}",
                            request.getURI().getPath()
                    );

                    return onError(
                            exchange,
                            "JWT token is invalid",
                            HttpStatus.UNAUTHORIZED
                    );
                }

                if (jwtUtil.isTokenExpired(token)) {

                    log.warn(
                            "Authentication failed: JWT token has expired for path: {}",
                            request.getURI().getPath()
                    );

                    return onError(
                            exchange,
                            "JWT token has expired",
                            HttpStatus.UNAUTHORIZED
                    );
                }

                String path = request.getURI().getPath();
                String method = request.getMethod().name();

                boolean isAdminRoute = path.startsWith("/api/v1/salons") && 
                                       (method.equals("POST") || method.equals("PUT") || method.equals("DELETE"));

                if (isAdminRoute && !"ROLE_ADMIN".equals(role)) {
                    log.warn("Access denied: User {} with role {} tried to access admin route {}", email, role, path);
                    return onError(
                            exchange,
                            "Access Denied: Only Admins can perform this action",
                            HttpStatus.FORBIDDEN
                    );
                }

                ServerHttpRequest mutatedRequest = request.mutate()
                        .header(
                                "X-User-Id",
                                id != null ? String.valueOf(id) : ""
                        )
                        .header(
                                "X-User-Email",
                                email
                        )
                        .header(
                                "X-User-Role",
                                role != null ? role : ""
                        )
                        .build();

                ServerWebExchange mutatedExchange = exchange
                        .mutate()
                        .request(mutatedRequest)
                        .build();

                log.debug(
                        "JWT authentication successful for user: {} | path: {}",
                        email,
                        request.getURI().getPath()
                );

                return chain.filter(mutatedExchange);

            } catch (ExpiredJwtException e) {

                log.warn(
                        "JWT token expired at path {}: {}",
                        request.getURI().getPath(),
                        e.getMessage()
                );

                return onError(
                        exchange,
                        "JWT token has expired",
                        HttpStatus.UNAUTHORIZED
                );

            } catch (SignatureException e) {

                log.warn(
                        "JWT signature validation failed at path {}: {}",
                        request.getURI().getPath(),
                        e.getMessage()
                );

                return onError(
                        exchange,
                        "JWT signature validation failed",
                        HttpStatus.UNAUTHORIZED
                );

            } catch (MalformedJwtException e) {

                log.warn(
                        "Malformed JWT token at path {}: {}",
                        request.getURI().getPath(),
                        e.getMessage()
                );

                return onError(
                        exchange,
                        "Malformed JWT token",
                        HttpStatus.UNAUTHORIZED
                );

            } catch (Exception e) {

                log.error(
                        "JWT validation error at path {}: {}",
                        request.getURI().getPath(),
                        e.getMessage(),
                        e
                );

                return onError(
                        exchange,
                        "Invalid JWT token",
                        HttpStatus.UNAUTHORIZED
                );
            }
        };
    }

    private Mono<Void> onError(
            ServerWebExchange exchange,
            String message,
            HttpStatus status
    ) {

        ServerHttpResponse response = exchange.getResponse();

        response.setStatusCode(status);

        response.getHeaders().setContentType(
                MediaType.APPLICATION_JSON
        );

        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .success(false)
                .status(status.value())
                .path(exchange.getRequest().getURI().getPath())
                .timestamp(LocalDateTime.now())
                .data(
                        ApiResponse.DataWrapper.<Void>builder()
                                .message(message)
                                .error(status.getReasonPhrase())
                                .build()
                )
                .build();

        try {

            byte[] bytes = objectMapper.writeValueAsBytes(apiResponse);

            DataBuffer buffer = response
                    .bufferFactory()
                    .wrap(bytes);

            return response.writeWith(
                    Mono.just(buffer)
            );

        } catch (JsonProcessingException e) {

            log.error(
                    "Error writing error response to JSON: {}",
                    e.getMessage(),
                    e
            );

            return response.setComplete();
        }
    }

    public static class Config {

    }
}