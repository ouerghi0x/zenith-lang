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
    private final List<IndexDef> indexes;
    private String sourceFile;
    private int sourceLine;
    private int sourceColumn = 1;
    private SourceLocation sourceLocation;

    public ModelDef(String name, List<FieldDef> fields, List<RelationDef> relations, CrudConfig crudConfig, List<CustomEndpointDef> customEndpoints) {
        this(name, fields, relations, crudConfig, customEndpoints, null);
    }

    public ModelDef(String name, List<FieldDef> fields, List<RelationDef> relations, CrudConfig crudConfig,
            List<CustomEndpointDef> customEndpoints, List<IndexDef> indexes) {
        this.name = name;
        this.fields = fields != null ? fields : new ArrayList<>();
        this.relations = relations != null ? relations : new ArrayList<>();
        this.crudConfig = crudConfig;
        this.customEndpoints = customEndpoints != null ? customEndpoints : new ArrayList<>();
        this.indexes = indexes != null ? indexes : new ArrayList<>();
    }

    /** Model-level index declarations only (e.g. {@code @index([a, b])}). */
    public List<IndexDef> getIndexes() {
        return Collections.unmodifiableList(indexes);
    }

    /**
     * All declared secondary indexes: inline field decorators ({@code @index}, {@code @uniqueIndex})
     * first in field order, followed by model-level composite declarations.
     */
    public List<IndexDef> getEffectiveIndexes() {
        List<IndexDef> all = new ArrayList<>();
        for (FieldDef f : fields) {
            for (AnnotationDef ann : f.getAnnotations()) {
                boolean isIndex = "index".equalsIgnoreCase(ann.getName());
                boolean isUniqueIndex = "uniqueIndex".equalsIgnoreCase(ann.getName());
                if (!isIndex && !isUniqueIndex) continue;
                Object nameArg = ann.getArgument("name");
                if (nameArg == null && ann.getArgument("0") instanceof String) {
                    nameArg = ann.getArgument("0");
                }
                Object uniqueArg = ann.getArgument("unique");
                boolean unique = isUniqueIndex || Boolean.TRUE.equals(uniqueArg) || "true".equals(String.valueOf(uniqueArg));
                IndexDef idx = new IndexDef(nameArg != null ? nameArg.toString() : null,
                        Collections.singletonList(f.getName()), unique, true);
                idx.setSourceFile(sourceFile);
                idx.setSourceLine(sourceLine);
                all.add(idx);
            }
        }
        all.addAll(indexes);
        return all;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
        updateSourceLocation();
    }

    public int getSourceLine() {
        return sourceLine;
    }

    public void setSourceLine(int sourceLine) {
        this.sourceLine = sourceLine;
        updateSourceLocation();
    }

    public int getSourceColumn() {
        return sourceColumn;
    }

    public void setSourceColumn(int sourceColumn) {
        this.sourceColumn = sourceColumn;
        updateSourceLocation();
    }

    public SourceLocation getSourceLocation() {
        if (sourceLocation == null && sourceFile != null) {
            sourceLocation = new SourceLocation(sourceFile, sourceLine > 0 ? sourceLine : 1, sourceColumn > 0 ? sourceColumn : 1);
        }
        return sourceLocation;
    }

    public void setSourceLocation(SourceLocation sourceLocation) {
        this.sourceLocation = sourceLocation;
        if (sourceLocation != null) {
            this.sourceFile = sourceLocation.getSourceFile();
            this.sourceLine = sourceLocation.getStartLine();
            this.sourceColumn = sourceLocation.getStartColumn();
        }
    }

    private void updateSourceLocation() {
        if (sourceFile != null && sourceLine > 0) {
            this.sourceLocation = new SourceLocation(sourceFile, sourceLine, sourceColumn > 0 ? sourceColumn : 1);
        }
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
