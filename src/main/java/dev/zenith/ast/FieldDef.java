package dev.zenith.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FieldDef {
    private final String name;
    private final TypeRef type;
    private final boolean optional;
    private final List<AnnotationDef> annotations;
    private final Object defaultValue;

    public FieldDef(String name, TypeRef type, boolean optional, List<AnnotationDef> annotations, Object defaultValue) {
        this.name = name;
        this.type = type;
        this.optional = optional;
        this.annotations = annotations != null ? annotations : new ArrayList<>();
        this.defaultValue = defaultValue;
    }

    public String getName() {
        return name;
    }

    public TypeRef getType() {
        return type;
    }

    public boolean isOptional() {
        return optional;
    }

    public List<AnnotationDef> getAnnotations() {
        return Collections.unmodifiableList(annotations);
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public boolean hasAnnotation(String annotationName) {
        for (AnnotationDef ann : annotations) {
            if (ann.getName().equalsIgnoreCase(annotationName)) {
                return true;
            }
        }
        return false;
    }

    public AnnotationDef getAnnotation(String annotationName) {
        for (AnnotationDef ann : annotations) {
            if (ann.getName().equalsIgnoreCase(annotationName)) {
                return ann;
            }
        }
        return null;
    }
}
