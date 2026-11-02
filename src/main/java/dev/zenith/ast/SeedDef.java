package dev.zenith.ast;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SeedDef {
    private final String entityName;
    private final List<Map<String, Object>> rows;

    public SeedDef(String entityName, List<Map<String, Object>> rows) {
        this.entityName = entityName;
        this.rows = rows != null ? rows : Collections.emptyList();
    }

    public String getEntityName() {
        return entityName;
    }

    public List<Map<String, Object>> getRows() {
        return Collections.unmodifiableList(rows);
    }
}
