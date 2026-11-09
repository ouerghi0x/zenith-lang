package dev.zenith.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Secondary database index declaration (Spec #02).
 *
 * <p>Created either from a model-level declaration
 * ({@code @index([fieldA, fieldB], name = "idx_custom")} / {@code @uniqueIndex([a, b])})
 * or from an inline field decorator ({@code email: String @index}).</p>
 */
public class IndexDef {
    private final String name;          // explicit user-defined name, or null for auto-generated
    private final List<String> fields;  // ordered DSL field / relation names
    private final boolean unique;
    private final boolean inline;       // true when declared via a field-level decorator
    private String sourceFile;
    private int sourceLine;

    public IndexDef(String name, List<String> fields, boolean unique, boolean inline) {
        this.name = (name != null && !name.trim().isEmpty()) ? name.trim() : null;
        this.fields = fields != null ? new ArrayList<>(fields) : new ArrayList<>();
        this.unique = unique;
        this.inline = inline;
    }

    public String getName() {
        return name;
    }

    public boolean hasExplicitName() {
        return name != null;
    }

    public List<String> getFields() {
        return Collections.unmodifiableList(fields);
    }

    public boolean isUnique() {
        return unique;
    }

    public boolean isInline() {
        return inline;
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

    @Override
    public String toString() {
        return (unique ? "@uniqueIndex" : "@index") + fields + (name != null ? "(name=" + name + ")" : "");
    }
}

