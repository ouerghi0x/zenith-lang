package dev.zenith.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModelDef {
    private final String name;
    private final List<FieldDef> fields;
    private final List<RelationDef> relations;
    private final CrudConfig crudConfig;
    private final List<CustomEndpointDef> customEndpoints;
    private String sourceFile;
    private int sourceLine;

    public ModelDef(String name, List<FieldDef> fields, List<RelationDef> relations, CrudConfig crudConfig, List<CustomEndpointDef> customEndpoints) {
        this.name = name;
        this.fields = fields != null ? fields : new ArrayList<>();
        this.relations = relations != null ? relations : new ArrayList<>();
        this.crudConfig = crudConfig;
        this.customEndpoints = customEndpoints != null ? customEndpoints : new ArrayList<>();
    }

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

    public boolean hasEndpoints() {
        return crudConfig != null || !customEndpoints.isEmpty();
    }

    public boolean hasCrud() {
        return crudConfig != null;
    }

    public boolean hasStorageFields() {
        for (FieldDef f : fields) {
            if (f.hasAnnotation("file") || f.hasAnnotation("image")) {
                return true;
            }
        }
        return false;
    }

    public boolean hasStorageEndpoints() {
        return crudConfig != null && hasStorageFields();
    }

    public String getName() {
        return name;
    }

    public List<FieldDef> getFields() {
        return Collections.unmodifiableList(fields);
    }

    public List<RelationDef> getRelations() {
        return Collections.unmodifiableList(relations);
    }

    public CrudConfig getCrudConfig() {
        return crudConfig;
    }

    public List<CustomEndpointDef> getCustomEndpoints() {
        return Collections.unmodifiableList(customEndpoints);
    }

    public FieldDef getIdField() {
        for (FieldDef field : fields) {
            if (field.hasAnnotation("id")) {
                return field;
            }
        }
        return null;
    }

    public FieldDef findField(String fieldName) {
        for (FieldDef field : fields) {
            if (field.getName().equalsIgnoreCase(fieldName)) {
                return field;
            }
        }
        return null;
    }

    public RelationDef findRelation(String name) {
        if (name == null) return null;
        for (RelationDef rel : relations) {
            if (rel.getFieldName().equalsIgnoreCase(name)
                    || (rel.getFieldName() + "Id").equalsIgnoreCase(name)
                    || ("id" + rel.getFieldName()).equalsIgnoreCase(name)) {
                return rel;
            }
        }
        return null;
    }
}
