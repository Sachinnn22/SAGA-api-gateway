package com.example.apigateway.filter;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;

@Component
public class RouteValidator {

    public static final List<String> openApiEndpoints = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/users/register"
    );

    public Predicate<ServerHttpRequest> isSecured =
            request -> {
                String path = request.getURI().getPath();
                String method = request.getMethod().name();
                
                // 1. Allow Auth and User Registration endpoints (Public)
                if (path.equals("/api/v1/auth/login") || 
                    path.equals("/api/v1/auth/register") || 
                    path.equals("/api/v1/auth/refresh") ||
                    path.equals("/api/v1/users/register")) {
                    return false;
                }
                
                // 2. Allow public viewing of Salons & Services (GET requests only)
                // Customers don't need a token to search/view salons and services
                if (path.startsWith("/api/v1/salons") && method.equalsIgnoreCase("GET")) {
                    return false;
                }
                
                // 3. Allow Eureka discovery service path
                if (path.contains("/eureka")) {
                    return false;
                }
                
                // All other endpoints require authentication (JWT Token)
                return true;
            };
}