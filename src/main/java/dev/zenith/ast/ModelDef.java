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

    public ModelDef(String name, List<FieldDef> fields, List<RelationDef> relations, CrudConfig crudConfig, List<CustomEndpointDef> customEndpoints) {
        this.name = name;
        this.fields = fields != null ? fields : new ArrayList<>();
        this.relations = relations != null ? relations : new ArrayList<>();
        this.crudConfig = crudConfig;
        this.customEndpoints = customEndpoints != null ? customEndpoints : new ArrayList<>();
    }

    public boolean hasEndpoints() {
        return crudConfig != null || !customEndpoints.isEmpty();
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
}
