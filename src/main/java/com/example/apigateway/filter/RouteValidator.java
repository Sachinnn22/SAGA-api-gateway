package com.example.apigateway.filter;

import org.springframework.http.HttpMethod; // 👈 අත්‍යවශ්‍යයි
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;

@Component
public class RouteValidator {

    public static final List<String> openApiEndpoints = List.of(
            "/api/v1/users/register",
            "/api/v1/users/login"
    );

    public Predicate<ServerHttpRequest> isSecured =
            request -> {
                String path = request.getURI().getPath();
                String method = request.getMethod().name();

                if (request.getMethod().equals(HttpMethod.OPTIONS)) {
                    return false;
                }

                if (openApiEndpoints.stream().anyMatch(path::contains)) {
                    return false;
                }

                if (path.startsWith("/api/v1/salons") && method.equalsIgnoreCase("GET")) {
                    return false;
                }

                if (path.contains("/eureka")) {
                    return false;
                }

                return true;
            };
}