package dev.zenith.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CustomEndpointDef {
    private final String method;
    private final String path;
    private final String returnType;
    private final String description;
    private final List<GuardDef> guards;
    private final List<String> actionStatements;
    private final List<String> roles; // canonical role access control
    private final Boolean requiresAuth; // null = default, true = authenticated, false = public
    private final Boolean userScoped; // null = auto-detect, true = filter by current user
    private final String query; // optional custom query / filter mapping like "orderNumber == trackingNumber"
    private final Map<String, String> paramTypes; // optional explicit parameter types: e.g. {"id": "UUID"}
    private final String bodyType; // optional body
    private RateLimitSpec rateLimit; // optional endpoint rate limiting
    private String cacheDuration; // optional cache duration e.g. "1h", "15m"
    private String sourceFile;
    private int sourceLine;

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public int getSourceLine() {
        return sourceLine;
    }

    public void setSourceLine(int sourceLine) {
        this.sourceLine = sourceLine;
    }

    public String getBodyType() {
        return bodyType;
    }

    public boolean hasBody() {
        return bodyType != null && !bodyType.trim().isEmpty();
    }

    public CustomEndpointDef(String method, String path, String returnType, String description, List<GuardDef> guards,
            List<String> actionStatements, Boolean requiresAuth, Boolean userScoped, String query,
            Map<String, String> paramTypes, String bodyType, RateLimitSpec rateLimit, List<String> roles) {
        this.method = method != null ? method.toUpperCase() : "GET";
        this.path = path != null ? path : "/";
        this.returnType = returnType != null ? returnType : "Void";
        this.description = description != null ? description : "";
        this.guards = guards != null ? guards : new ArrayList<>();
        this.actionStatements = actionStatements != null ? actionStatements : new ArrayList<>();
        this.roles = roles != null ? new ArrayList<>(roles) : new ArrayList<>();
        // If roles is explicitly set, requiresAuth is implicitly true unless specified otherwise
        this.requiresAuth = (requiresAuth != null) ? requiresAuth : (!this.roles.isEmpty() ? Boolean.TRUE : null);
        this.userScoped = userScoped;
        this.query = query;
        this.paramTypes = paramTypes != null ? paramTypes : new java.util.LinkedHashMap<>();
        this.bodyType = bodyType != null ? bodyType : "";
        this.rateLimit = rateLimit;
    }

    public CustomEndpointDef(String method, String path, String returnType, String description, List<GuardDef> guards,
            List<String> actionStatements, Boolean requiresAuth, Boolean userScoped, String query,
            Map<String, String> paramTypes, String bodyType, RateLimitSpec rateLimit) {
        this(method, path, returnType, description, guards, actionStatements, requiresAuth, userScoped, query, paramTypes, bodyType, rateLimit, Collections.emptyList());
    }

    public CustomEndpointDef(String method, String path, String returnType, String description, List<GuardDef> guards,
            List<String> actionStatements, Boolean requiresAuth, Boolean userScoped, String query,
            Map<String, String> paramTypes, String bodyType) {
        this(method, path, returnType, description, guards, actionStatements, requiresAuth, userScoped, query, paramTypes, bodyType, null);
    }

    public CustomEndpointDef(String method, String path, String returnType, String description, List<GuardDef> guards,
            List<String> actionStatements, Boolean requiresAuth, Boolean userScoped, String query) {
        this(method, path, returnType, description, guards, actionStatements, requiresAuth, userScoped, query,
                Collections.emptyMap(), null);
    }

    public CustomEndpointDef(String method, String path, String returnType, String description, List<GuardDef> guards,
            List<String> actionStatements, Boolean requiresAuth, Boolean userScoped) {
        this(method, path, returnType, description, guards, actionStatements, requiresAuth, userScoped, null);
    }

    public CustomEndpointDef(String method, String path, String returnType, String description, List<GuardDef> guards,
            List<String> actionStatements, Boolean requiresAuth) {
        this(method, path, returnType, description, guards, actionStatements, requiresAuth, null);
    }

    public CustomEndpointDef(String method, String path, String returnType, String description, List<GuardDef> guards,
            List<String> actionStatements) {
        this(method, path, returnType, description, guards, actionStatements, null);
    }

    public CustomEndpointDef(String method, String path, String returnType, String description) {
        this(method, path, returnType, description, Collections.emptyList(), Collections.emptyList(), null);
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getReturnType() {
        return returnType;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getRoles() {
        return Collections.unmodifiableList(roles);
    }

    public boolean hasRoles() {
        return !roles.isEmpty();
    }

    public List<GuardDef> getGuards() {
        return Collections.unmodifiableList(guards);
    }

    public List<String> getActionStatements() {
        return Collections.unmodifiableList(actionStatements);
    }

    public Boolean getRequiresAuth() {
        return requiresAuth;
    }

    public Boolean getUserScoped() {
        return userScoped;
    }

    public String getQuery() {
        return query;
    }

    public Map<String, String> getParamTypes() {
        return Collections.unmodifiableMap(paramTypes);
    }

    public boolean isUserScoped(String methodName) {
        if (Boolean.TRUE.equals(userScoped))
            return true;
        if (Boolean.FALSE.equals(userScoped))
            return false;
        String p = path != null ? path.toLowerCase() : "";
        String m = methodName != null ? methodName.toLowerCase() : "";
        return p.contains("/my") || p.contains("/me") || p.contains("mine") || m.startsWith("my")
                || m.contains("myorders");
    }

    public boolean isPublic() {
        return Boolean.FALSE.equals(requiresAuth);
    }

    public boolean hasLogic() {
        return !guards.isEmpty() || !actionStatements.isEmpty();
    }

    public RateLimitSpec getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimitSpec rateLimit) {
        this.rateLimit = rateLimit;
    }

    public boolean hasRateLimit() {
        return rateLimit != null;
    }

    public String getCacheDuration() {
        return cacheDuration;
    }

    public void setCacheDuration(String cacheDuration) {
        this.cacheDuration = cacheDuration;
    }

    public boolean hasCache() {
        return cacheDuration != null && !cacheDuration.isEmpty() && !"false".equalsIgnoreCase(cacheDuration) && !"0s".equalsIgnoreCase(cacheDuration);
    }
}
