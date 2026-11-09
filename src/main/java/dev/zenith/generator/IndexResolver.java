package dev.zenith.generator;

import dev.zenith.ast.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Resolves declared {@link IndexDef}s (Spec #02) into physical index definitions:
 * table name, ordered snake_case column list and a deterministic, length-safe index name.
 *
 * <p>Shared by {@link FlywayGenerator}, {@link EntityGenerator} and the semantic analyzer so that
 * SQL DDL and JPA {@code @Index} annotations are always identical.</p>
 */
public final class IndexResolver {

    /** PostgreSQL identifier limit (MySQL allows 64; 63 is safe for both). */
    public static final int MAX_IDENTIFIER_LENGTH = 63;

    private static final Set<String> SQL_RESERVED = new HashSet<>(Arrays.asList(
            "all", "and", "any", "as", "asc", "between", "by", "case", "check", "column", "constraint",
            "create", "cross", "current_date", "current_time", "current_timestamp", "current_user",
            "default", "delete", "desc", "distinct", "drop", "else", "end", "except", "exists", "false",
            "fetch", "for", "foreign", "from", "full", "grant", "group", "having", "in", "index", "inner",
            "insert", "intersect", "into", "is", "join", "key", "left", "like", "limit", "not", "null",
            "offset", "on", "or", "order", "outer", "primary", "references", "right", "rows", "select",
            "session_user", "table", "then", "to", "true", "union", "unique", "update", "user", "using",
            "value", "values", "when", "where", "window", "with"));

    private IndexResolver() {
    }

    /** A fully resolved physical index. */
    public static final class ResolvedIndex {
        public final String name;
        public final String tableName;
        public final List<String> columns;
        public final boolean unique;
        public final IndexDef source;

        ResolvedIndex(String name, String tableName, List<String> columns, boolean unique, IndexDef source) {
            this.name = name;
            this.tableName = tableName;
            this.columns = Collections.unmodifiableList(columns);
            this.unique = unique;
            this.source = source;
        }

        /** Column signature used for redundancy detection, e.g. {@code "customer_id,status"}. */
        public String signature() {
            return String.join(",", columns);
        }
    }

    public static String tableName(ModelDef model) {
        return TypeHelper.toSnakeCase(TypeHelper.pluralize(model.getName()));
    }

    /**
     * Resolves a DSL field or relation name to its physical column, or {@code null} if unknown /
     * not indexable as a single column (collection relations).
     */
    public static String resolveColumn(ModelDef model, String dslName) {
        FieldDef f = model.findField(dslName);
        if (f != null) {
            return TypeHelper.toSnakeCase(f.getName());
        }
        for (RelationDef rel : model.getRelations()) {
            if (rel.getFieldName().equalsIgnoreCase(dslName)
                    && (rel.getRelationType() == RelationType.MANY_TO_ONE
                        || rel.getRelationType() == RelationType.ONE_TO_ONE)) {
                return TypeHelper.toSnakeCase(rel.getFieldName()) + "_id";
            }
        }
        return null;
    }

    /**
     * Resolves every declared index on the model. Indexes on the primary key and exact duplicates
     * (same column list) are silently normalized away; first declaration wins.
     */
    public static List<ResolvedIndex> resolve(ModelDef model) {
        String table = tableName(model);
        FieldDef idField = model.getIdField();
        String idCol = idField != null ? TypeHelper.toSnakeCase(idField.getName()) : "id";

        List<ResolvedIndex> result = new ArrayList<>();
        Set<String> seenSignatures = new HashSet<>();
        for (IndexDef idx : model.getEffectiveIndexes()) {
            List<String> cols = new ArrayList<>();
            boolean valid = true;
            for (String f : idx.getFields()) {
                String col = resolveColumn(model, f);
                if (col == null) {
                    valid = false;
                    break;
                }
                cols.add(col);
            }
            if (!valid || cols.isEmpty()) continue;
            // Primary key is already indexed by its PK constraint
            if (cols.size() == 1 && cols.get(0).equals(idCol)) continue;

            String sig = String.join(",", cols);
            if (!seenSignatures.add(sig)) continue;

            String name = idx.hasExplicitName() ? idx.getName() : generateName(table, cols, idx.isUnique());
            result.add(new ResolvedIndex(name, table, cols, idx.isUnique(), idx));
        }
        return result;
    }

    /**
     * Deterministic convention: {@code idx_<table>_<col1>_<col2>} (or {@code uidx_} for unique).
     * Names longer than 63 chars are truncated and suffixed with an 8-char hash to stay unique.
     */
    public static String generateName(String table, List<String> columns, boolean unique) {
        String base = (unique ? "uidx_" : "idx_") + table + "_" + String.join("_", columns);
        return shorten(base);
    }

    public static String shorten(String identifier) {
        if (identifier.length() <= MAX_IDENTIFIER_LENGTH) {
            return identifier;
        }
        String hash = hash8(identifier);
        String prefix = identifier.substring(0, MAX_IDENTIFIER_LENGTH - hash.length() - 1);
        while (prefix.endsWith("_")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix + "_" + hash;
    }

    private static String hash8(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            return String.format("%08x", s.hashCode());
        }
    }

    public static boolean isReserved(String column) {
        return SQL_RESERVED.contains(column.toLowerCase(Locale.ROOT));
    }

    /** Quotes a column for DDL if it collides with a SQL reserved word. */
    public static String quote(String column, boolean isMysql) {
        if (!isReserved(column)) return column;
        return isMysql ? "`" + column + "`" : "\"" + column + "\"";
    }

    /** Builds a dialect-aware {@code CREATE [UNIQUE] INDEX} statement. */
    public static String toCreateStatement(String name, String table, List<String> columns, boolean unique, boolean isMysql) {
        StringBuilder sb = new StringBuilder("CREATE ");
        if (unique) sb.append("UNIQUE ");
        sb.append("INDEX ");
        // MySQL 8 does not support CREATE INDEX IF NOT EXISTS
        if (!isMysql) sb.append("IF NOT EXISTS ");
        sb.append(name).append(" ON ").append(table).append(" (");
        List<String> quoted = new ArrayList<>();
        for (String c : columns) quoted.add(quote(c, isMysql));
        sb.append(String.join(", ", quoted)).append(");");
        return sb.toString();
    }
}

