package dev.zenith.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CrudConfig {
    private final boolean enabled;
    private final boolean paginate;
    private final List<String> searchFields;
    private final List<String> filterFields;
    private final List<String> sortFields;
    private final Boolean requiresAuth; // null = default convention, true = authenticated, false = public
    private final List<String> defaultRoles; // default roles for all operations in this crud block
    private final Map<String, List<String>> operationRoles; // granular roles e.g. "delete" -> ["ADMIN"], "getAll" -> ["public"]
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

    public CrudConfig(boolean enabled, boolean paginate, List<String> searchFields, List<String> filterFields,
                      List<String> sortFields, Boolean requiresAuth, List<String> defaultRoles,
                      Map<String, List<String>> operationRoles) {
        this.enabled = enabled;
        this.paginate = paginate;
        this.searchFields = searchFields != null ? searchFields : new ArrayList<>();
        this.filterFields = filterFields != null ? filterFields : new ArrayList<>();
        this.sortFields = sortFields != null ? sortFields : new ArrayList<>();
        this.requiresAuth = requiresAuth;
        this.defaultRoles = defaultRoles != null ? defaultRoles : new ArrayList<>();
        this.operationRoles = operationRoles != null ? operationRoles : new LinkedHashMap<>();
    }

    public CrudConfig(boolean enabled, boolean paginate, List<String> searchFields, List<String> filterFields, List<String> sortFields, Boolean requiresAuth) {
        this(enabled, paginate, searchFields, filterFields, sortFields, requiresAuth, Collections.emptyList(), Collections.emptyMap());
    }

    public CrudConfig(boolean enabled, boolean paginate, List<String> searchFields, List<String> filterFields, List<String> sortFields) {
        this(enabled, paginate, searchFields, filterFields, sortFields, null, Collections.emptyList(), Collections.emptyMap());
    }

    public static CrudConfig defaults() {
        return new CrudConfig(true, true, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null, Collections.emptyList(), Collections.emptyMap());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isPaginate() {
        return paginate;
    }

    public List<String> getSearchFields() {
        return Collections.unmodifiableList(searchFields);
    }

    public List<String> getFilterFields() {
        return Collections.unmodifiableList(filterFields);
    }

    public List<String> getSortFields() {
        return Collections.unmodifiableList(sortFields);
    }

    public Boolean getRequiresAuth() {
        return requiresAuth;
    }

    public boolean isPublic() {
        return Boolean.FALSE.equals(requiresAuth);
    }

    public List<String> getDefaultRoles() {
        return Collections.unmodifiableList(defaultRoles);
    }

    public Map<String, List<String>> getOperationRoles() {
        return Collections.unmodifiableMap(operationRoles);
    }

    public List<String> getRolesForOperation(String operation) {
        if (operation != null && operationRoles.containsKey(operation.toLowerCase())) {
            return operationRoles.get(operation.toLowerCase());
        }
        return defaultRoles;
    }

    public boolean isOperationPublic(String operation) {
        if (operation == null) return false;
        List<String> roles = operationRoles.get(operation.toLowerCase());
        if (roles != null && roles.size() == 1 && "public".equalsIgnoreCase(roles.get(0))) {
            return true;
        }
        return false;
    }
}
