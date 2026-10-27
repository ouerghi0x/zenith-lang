package dev.zenith.ast;

public class RelationDef {
    private final String fieldName;
    private final String targetModel;
    private final RelationType relationType;
    private final String mappedBy;
    private final boolean optional;

    public RelationDef(String fieldName, String targetModel, RelationType relationType, String mappedBy, boolean optional) {
        this.fieldName = fieldName;
        this.targetModel = targetModel;
        this.relationType = relationType != null ? relationType : RelationType.MANY_TO_ONE;
        this.mappedBy = mappedBy;
        this.optional = optional;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getTargetModel() {
        return targetModel;
    }

    public RelationType getRelationType() {
        return relationType;
    }

    public String getMappedBy() {
        return mappedBy;
    }

    public boolean isOptional() {
        return optional;
    }

    public boolean isCollection() {
        return relationType == RelationType.ONE_TO_MANY || relationType == RelationType.MANY_TO_MANY;
    }
}
