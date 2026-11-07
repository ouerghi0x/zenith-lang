package dev.zenith.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DtoDef {
    private final String name;
    private final List<FieldDef> fields;
    private String sourceFile;
    private int sourceLine;

    public DtoDef(String name, List<FieldDef> fields) {
        this.name = name;
        this.fields = fields != null ? fields : new ArrayList<>();
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

    public String getName() {
        return name;
    }

    public List<FieldDef> getFields() {
        return Collections.unmodifiableList(fields);
    }
}
