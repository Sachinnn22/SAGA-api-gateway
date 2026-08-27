package com.example.apigateway.filter;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;

@Component
public class RouteValidator {

    public static final List<String> openApiEndpoints = List.of(
            "/api/v1/users/register",
            "/api/v1/users/login",
            "/api/v1/users/health"
    );

    public Predicate<ServerHttpRequest> isSecured =
            request -> {

                String path = request.getURI().getPath();
                String method = request.getMethod().name();

                // CORS preflight
                if (request.getMethod().equals(HttpMethod.OPTIONS)) {
                    return false;
                }

                // Public endpoints
                if (openApiEndpoints.stream().anyMatch(path::equals)) {
                    return false;
                }

                // Public salon GET endpoints
                if (path.startsWith("/api/v1/salons")
                        && method.equalsIgnoreCase("GET")) {
                    return false;
                }

                // Eureka
                if (path.contains("/eureka")) {
                    return false;
                }

                // Everything else requires authentication
                return true;
            };
}