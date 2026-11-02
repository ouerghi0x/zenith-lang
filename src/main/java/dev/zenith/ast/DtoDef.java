package dev.zenith.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DtoDef {
    private final String name;
    private final List<FieldDef> fields;

    public DtoDef(String name, List<FieldDef> fields) {
        this.name = name;
        this.fields = fields != null ? fields : new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public List<FieldDef> getFields() {
        return Collections.unmodifiableList(fields);
    }
}
