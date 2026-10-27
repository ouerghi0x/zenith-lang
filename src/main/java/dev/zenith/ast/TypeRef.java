package dev.zenith.ast;

import java.util.Collections;
import java.util.Map;

public class TypeRef {
    private final String name;
    private final boolean isList;
    private final String genericType;
    private final Map<String, Object> params;

    public TypeRef(String name, boolean isList, String genericType, Map<String, Object> params) {
        this.name = name;
        this.isList = isList;
        this.genericType = genericType;
        this.params = params != null ? params : Collections.emptyMap();
    }

    public static TypeRef simple(String name) {
        return new TypeRef(name, false, null, Collections.emptyMap());
    }

    public static TypeRef simpleWithParams(String name, Map<String, Object> params) {
        return new TypeRef(name, false, null, params);
    }

    public static TypeRef list(String genericType) {
        return new TypeRef("List", true, genericType, Collections.emptyMap());
    }

    public String getName() {
        return name;
    }

    public boolean isList() {
        return isList;
    }

    public String getGenericType() {
        return genericType;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    @Override
    public String toString() {
        if (isList) return "List<" + genericType + ">";
        if (!params.isEmpty()) return name + params;
        return name;
    }
}
