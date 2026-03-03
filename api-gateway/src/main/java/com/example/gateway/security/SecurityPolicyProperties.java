package com.example.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "security.authz")
public class SecurityPolicyProperties {

    private boolean enforceVersionPrefix = true;
    private String requiredPrefix = "/api/v1";
    private List<RouteRule> publicRoutes = new ArrayList<>();
    private List<RouteRule> policies = new ArrayList<>();

    public boolean isEnforceVersionPrefix() {
        return enforceVersionPrefix;
    }

    public void setEnforceVersionPrefix(boolean enforceVersionPrefix) {
        this.enforceVersionPrefix = enforceVersionPrefix;
    }

    public String getRequiredPrefix() {
        return requiredPrefix;
    }

    public void setRequiredPrefix(String requiredPrefix) {
        this.requiredPrefix = requiredPrefix;
    }

    public List<RouteRule> getPublicRoutes() {
        return publicRoutes;
    }

    public void setPublicRoutes(List<RouteRule> publicRoutes) {
        this.publicRoutes = publicRoutes;
    }

    public List<RouteRule> getPolicies() {
        return policies;
    }

    public void setPolicies(List<RouteRule> policies) {
        this.policies = policies;
    }

    public static class RouteRule {
        private String id;
        private String pathPattern;
        private List<String> methods = new ArrayList<>();
        private List<String> roles = new ArrayList<>();
        private boolean ownerCheck;
        private String ownerPathVariable = "id";

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getPathPattern() {
            return pathPattern;
        }

        public void setPathPattern(String pathPattern) {
            this.pathPattern = pathPattern;
        }

        public List<String> getMethods() {
            return methods;
        }

        public void setMethods(List<String> methods) {
            this.methods = methods;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }

        public boolean isOwnerCheck() {
            return ownerCheck;
        }

        public void setOwnerCheck(boolean ownerCheck) {
            this.ownerCheck = ownerCheck;
        }

        public String getOwnerPathVariable() {
            return ownerPathVariable;
        }

        public void setOwnerPathVariable(String ownerPathVariable) {
            this.ownerPathVariable = ownerPathVariable;
        }
    }
}
