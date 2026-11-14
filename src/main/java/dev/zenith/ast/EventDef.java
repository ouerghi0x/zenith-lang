package dev.zenith.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable wire event declaration. */
public class EventDef {
    private final String name;
    private final List<FieldDef> fields;
    private String sourceFile;
    private int sourceLine;

    public EventDef(String name, List<FieldDef> fields) {
        this.name = name;
        this.fields = fields != null ? new ArrayList<>(fields) : new ArrayList<>();
    }

    public String getName() { return name; }
    public List<FieldDef> getFields() { return Collections.unmodifiableList(fields); }
    public String getSourceFile() { return sourceFile; }
    public int getSourceLine() { return sourceLine; }
    public void setSourceFile(String value) { sourceFile = value; }
    public void setSourceLine(int value) { sourceLine = value; }
}
