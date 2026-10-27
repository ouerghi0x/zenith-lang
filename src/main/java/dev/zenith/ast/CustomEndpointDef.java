package dev.zenith.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CustomEndpointDef {
    private final String method;
    private final String path;
    private final String returnType;
    private final String description;
    private final List<GuardDef> guards;
    private final List<String> actionStatements;

    public CustomEndpointDef(String method, String path, String returnType, String description, List<GuardDef> guards, List<String> actionStatements) {
        this.method = method != null ? method.toUpperCase() : "GET";
        this.path = path != null ? path : "/";
        this.returnType = returnType != null ? returnType : "Void";
        this.description = description != null ? description : "";
        this.guards = guards != null ? guards : new ArrayList<>();
        this.actionStatements = actionStatements != null ? actionStatements : new ArrayList<>();
    }

    public CustomEndpointDef(String method, String path, String returnType, String description) {
        this(method, path, returnType, description, Collections.emptyList(), Collections.emptyList());
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

    public boolean hasLogic() {
        return !guards.isEmpty() || !actionStatements.isEmpty();
    }
}
