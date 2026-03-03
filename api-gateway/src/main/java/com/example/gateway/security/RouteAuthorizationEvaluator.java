package com.example.gateway.security;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RouteAuthorizationEvaluator {

    private final SecurityPolicyProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RouteAuthorizationEvaluator(SecurityPolicyProperties properties) {
        this.properties = properties;
    }

    public boolean isVersionPrefixRequired() {
        return properties.isEnforceVersionPrefix();
    }

    public String requiredPrefix() {
        return normalizePrefix(properties.getRequiredPrefix());
    }

    public boolean isVersionedApiPath(String path) {
        String prefix = requiredPrefix();
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    public boolean isApiPath(String path) {
        return path.equals("/api") || path.startsWith("/api/");
    }

    public boolean isPublicRoute(String path, HttpMethod method) {
        for (SecurityPolicyProperties.RouteRule rule : safeRules(properties.getPublicRoutes())) {
            if (matches(rule, path, method)) {
                return true;
            }
        }
        return false;
    }

    public AuthorizationDecision authorize(String path, HttpMethod method, String role, String userId) {
        for (SecurityPolicyProperties.RouteRule rule : safeRules(properties.getPolicies())) {
            if (!matches(rule, path, method)) {
                continue;
            }

            Set<String> allowedRoles = normalizeSet(rule.getRoles());
            String normalizedRole = normalize(role);
            if (!allowedRoles.contains(normalizedRole)) {
                return AuthorizationDecision.deny("FORBIDDEN", "Forbidden");
            }

            if (rule.isOwnerCheck() && !"ADMIN".equals(normalizedRole)) {
                Map<String, String> vars = extractPathVariables(rule.getPathPattern(), path);
                String ownerPathVariable = rule.getOwnerPathVariable() == null || rule.getOwnerPathVariable().isBlank()
                        ? "id"
                        : rule.getOwnerPathVariable();
                String ownerId = vars.get(ownerPathVariable);
                if (ownerId == null || !ownerId.equals(userId)) {
                    return AuthorizationDecision.deny("FORBIDDEN", "Forbidden");
                }
            }

            return AuthorizationDecision.allow();
        }

        if (isVersionedApiPath(path)) {
            return AuthorizationDecision.deny("FORBIDDEN", "Forbidden");
        }

        return AuthorizationDecision.allow();
    }

    private boolean matches(SecurityPolicyProperties.RouteRule rule, String path, HttpMethod method) {
        if (rule == null || rule.getPathPattern() == null || rule.getPathPattern().isBlank()) {
            return false;
        }
        if (!pathMatcher.match(rule.getPathPattern(), path)) {
            return false;
        }

        List<String> methods = rule.getMethods();
        if (methods == null || methods.isEmpty()) {
            return true;
        }
        String requestMethod = method == null ? "" : method.name();
        return methods.stream()
                .filter(Objects::nonNull)
                .map(this::normalize)
                .anyMatch(requestMethod::equals);
    }

    private Map<String, String> extractPathVariables(String pattern, String path) {
        try {
            return pathMatcher.extractUriTemplateVariables(pattern, path);
        } catch (IllegalStateException ex) {
            return Collections.emptyMap();
        }
    }

    private List<SecurityPolicyProperties.RouteRule> safeRules(List<SecurityPolicyProperties.RouteRule> rules) {
        return rules == null ? Collections.emptyList() : rules;
    }

    private Set<String> normalizeSet(List<String> values) {
        if (values == null) {
            return Collections.emptySet();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(this::normalize)
                .collect(Collectors.toSet());
    }

    private String normalizePrefix(String value) {
        if (value == null || value.isBlank()) {
            return "/api/v1";
        }
        String trimmed = value.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public record AuthorizationDecision(boolean allowed, String code, String message) {
        public static AuthorizationDecision allow() {
            return new AuthorizationDecision(true, null, null);
        }

        public static AuthorizationDecision deny(String code, String message) {
            return new AuthorizationDecision(false, code, message);
        }
    }
}
