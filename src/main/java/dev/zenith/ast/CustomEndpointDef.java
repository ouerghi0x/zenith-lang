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
    private final Boolean requiresAuth; // null = default, true = authenticated, false = public
    private final Boolean userScoped; // null = auto-detect, true = filter by current user
    private final String query; // optional custom query / filter mapping like "orderNumber == trackingNumber"
    private final Map<String, String> paramTypes; // optional explicit parameter types: e.g. {"id": "UUID"}

    public CustomEndpointDef(String method, String path, String returnType, String description, List<GuardDef> guards, List<String> actionStatements, Boolean requiresAuth, Boolean userScoped, String query, Map<String, String> paramTypes) {
        this.method = method != null ? method.toUpperCase() : "GET";
        this.path = path != null ? path : "/";
        this.returnType = returnType != null ? returnType : "Void";
        this.description = description != null ? description : "";
        this.guards = guards != null ? guards : new ArrayList<>();
        this.actionStatements = actionStatements != null ? actionStatements : new ArrayList<>();
        this.requiresAuth = requiresAuth;
        this.userScoped = userScoped;
        this.query = query;
        this.paramTypes = paramTypes != null ? paramTypes : new java.util.LinkedHashMap<>();
    }

    public CustomEndpointDef(String method, String path, String returnType, String description, List<GuardDef> guards, List<String> actionStatements, Boolean requiresAuth, Boolean userScoped, String query) {
        this(method, path, returnType, description, guards, actionStatements, requiresAuth, userScoped, query, Collections.emptyMap());
    }

    public CustomEndpointDef(String method, String path, String returnType, String description, List<GuardDef> guards, List<String> actionStatements, Boolean requiresAuth, Boolean userScoped) {
        this(method, path, returnType, description, guards, actionStatements, requiresAuth, userScoped, null);
    }

    public CustomEndpointDef(String method, String path, String returnType, String description, List<GuardDef> guards, List<String> actionStatements, Boolean requiresAuth) {
        this(method, path, returnType, description, guards, actionStatements, requiresAuth, null);
    }

    public CustomEndpointDef(String method, String path, String returnType, String description, List<GuardDef> guards, List<String> actionStatements) {
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
        if (Boolean.TRUE.equals(userScoped)) return true;
        if (Boolean.FALSE.equals(userScoped)) return false;
        String p = path != null ? path.toLowerCase() : "";
        String m = methodName != null ? methodName.toLowerCase() : "";
        return p.contains("/my") || p.contains("/me") || p.contains("mine") || m.startsWith("my") || m.contains("myorders");
    }

    public boolean isPublic() {
        return Boolean.FALSE.equals(requiresAuth);
    }

    public boolean hasLogic() {
        return !guards.isEmpty() || !actionStatements.isEmpty();
    }
}
