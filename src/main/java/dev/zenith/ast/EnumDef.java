package dev.zenith.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EnumDef {
    private final String name;
    private final List<String> values;
    private String sourceFile;
    private int sourceLine;

    public EnumDef(String name, List<String> values) {
        this.name = name;
        this.values = values != null ? values : new ArrayList<>();
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

    public List<String> getValues() {
        return Collections.unmodifiableList(values);
    }
}
