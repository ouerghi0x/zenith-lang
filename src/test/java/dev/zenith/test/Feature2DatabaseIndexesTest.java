package dev.zenith.test;

import dev.zenith.analyzer.AnalysisException;
import dev.zenith.ast.IndexDef;
import dev.zenith.ast.ModelDef;
import dev.zenith.ast.ZenithFile;
import dev.zenith.generator.JavaSourceFile;
import dev.zenith.parser.ParseException;

import java.util.List;

import static dev.zenith.test.TestHarness.*;

/**
 * Feature #2: Database Indexes (@index / @uniqueIndex) — opaque-box tests.
 * T1 = parsing, T2 = semantic validation, T3 = Flyway SQL generation, T4 = JPA entity generation.
 */
public class Feature2DatabaseIndexesTest {

    // ------------------------------------------------------------------ helpers

    private static String schema(String db, String models) {
        return "config {\n    app: \"IdxApp\"\n    package: \"dev.zenith.idx\"\n    database: " + db + "\n    flyway: true\n}\n\n" + models;
    }

    private static final String CUSTOMER = "model Customer {\n    id: UUID @id @auto\n    email: String\n}\n\n";

    private static String orderModel(String extra) {
        return schema("postgres", CUSTOMER
                + "model Order {\n"
                + "    id: UUID @id @auto\n"
                + "    orderNumber: String\n"
                + "    status: String\n"
                + "    totalAmount: Decimal\n"
                + "    createdAt: DateTime @autoNow\n"
                + "    relations {\n"
                + "        customer: Customer? @relation(manyToOne)\n"
                + "    }\n"
                + extra
                + "}\n");
    }

    private static ModelDef model(ZenithFile f, String name) {
        for (ModelDef m : f.getModels()) if (m.getName().equals(name)) return m;
        throw new AssertionError("model not found: " + name);
    }

    private static String source(List<JavaSourceFile> files, String suffix) {
        for (JavaSourceFile s : files) if (s.getRelativePath().replace('\\', '/').endsWith(suffix)) return s.getContent();
        throw new AssertionError("generated file not found: " + suffix);
    }

    private static String sql(String dsl) {
        return source(generateAllSources(dsl), "V1__initial_schema.sql");
    }

    private static void expectAnalysisError(String dsl, String fragment) {
        try {
            analyze(dsl);
        } catch (AnalysisException e) {
            assertContains(e.getMessage(), fragment, "analysis error message");
            return;
        }
        throw new AssertionError("Expected AnalysisException containing: " + fragment);
    }

    // ------------------------------------------------------------------ T1: parsing

    public static void T1_01_parse_inline_field_index() {
        String dsl = schema("postgres", "model Product {\n    id: UUID @id @auto\n    sku: String @index\n}\n");
        List<IndexDef> idx = model(parse(dsl), "Product").getEffectiveIndexes();
        assertEquals(1, idx.size(), "one effective index");
        assertEquals(List.of("sku"), idx.get(0).getFields(), "indexed field");
        assertTrue(idx.get(0).isInline(), "inline flag");
        assertFalse(idx.get(0).isUnique(), "not unique");
    }

    public static void T1_02_parse_model_level_composite_preserves_order() {
        IndexDef idx = model(parse(orderModel("    @index([status, createdAt])\n")), "Order").getIndexes().get(0);
        assertEquals(List.of("status", "createdAt"), idx.getFields(), "ordered composite fields");
        assertFalse(idx.isInline(), "model-level");
    }

    public static void T1_03_parse_custom_index_name() {
        IndexDef idx = model(parse(orderModel("    @index([status, totalAmount], name = \"idx_order_status_total\")\n")), "Order").getIndexes().get(0);
        assertEquals("idx_order_status_total", idx.getName(), "custom name");
    }

    public static void T1_04_parse_unique_index_model_level() {
        IndexDef idx = model(parse(orderModel("    @uniqueIndex([orderNumber, customer])\n")), "Order").getIndexes().get(0);
        assertTrue(idx.isUnique(), "@uniqueIndex is unique");
    }

    public static void T1_05_parse_named_fields_and_unique_args() {
        IndexDef idx = model(parse(orderModel("    @index(fields = [status, orderNumber], unique = true)\n")), "Order").getIndexes().get(0);
        assertEquals(List.of("status", "orderNumber"), idx.getFields(), "fields= argument");
        assertTrue(idx.isUnique(), "unique = true argument");
    }

    public static void T1_06_parse_inline_unique_index_with_name() {
        String dsl = schema("postgres", "model Product {\n    id: UUID @id @auto\n    sku: String @uniqueIndex(name = \"uk_product_sku\")\n}\n");
        IndexDef idx = model(parse(dsl), "Product").getEffectiveIndexes().get(0);
        assertTrue(idx.isUnique(), "inline unique");
        assertEquals("uk_product_sku", idx.getName(), "inline name");
    }

    public static void T1_07_reject_unknown_model_level_annotation() {
        try {
            parse(orderModel("    @cache([status])\n"));
        } catch (ParseException e) {
            assertContains(e.getMessage(), "Only '@index", "parse error message");
            return;
        }
        throw new AssertionError("Expected ParseException for @cache at model scope");
    }

    public static void T1_08_reject_empty_field_list() {
        try {
            parse(orderModel("    @index([])\n"));
        } catch (ParseException e) {
            assertContains(e.getMessage(), "must not be empty", "parse error message");
            return;
        }
        throw new AssertionError("Expected ParseException for empty field list");
    }

    public static void T1_09_index_survives_semantic_analysis() {
        ModelDef m = model(analyze(orderModel("    @index([status, createdAt])\n")), "Order");
        assertEquals(1, m.getIndexes().size(), "validated model keeps model-level indexes");
    }

    public static void T1_10_parse_model_indexes_placed_before_relations_block() {
        String dsl = schema("postgres", CUSTOMER
                + "model Order {\n"
                + "    id: UUID @id @auto\n"
                + "    orderNumber: String @unique\n"
                + "    status: String\n"
                + "    createdAt: DateTime @autoNow\n"
                + "    @index([customer, status])\n"
                + "    @index([status, createdAt], name = \"idx_orders_status_timeline\")\n"
                + "    @uniqueIndex([orderNumber, customer])\n"
                + "    relations {\n"
                + "        customer: Customer @relation(manyToOne)\n"
                + "    }\n"
                + "}\n");
        ZenithFile file = analyze(dsl);
        ModelDef order = model(file, "Order");
        assertEquals(3, order.getIndexes().size(), "all 3 model-level indexes parsed even when placed above relations");
        String entity = source(generateAllSources(dsl), "entity/Order.java");
        assertContains(entity, "idx_orders_customer_id_status", "composite FK + status index");
        assertContains(entity, "idx_orders_status_timeline", "custom named index");
        assertContains(entity, "uidx_orders_order_number_customer_id", "unique composite index");
    }

    // ------------------------------------------------------------------ T2: semantic validation

    public static void T2_01_reject_unknown_field_with_suggestion() {
        expectAnalysisError(orderModel("    @index([staus, createdAt])\n"), "Did you mean 'status'");
    }

    public static void T2_02_reject_redundant_inline_and_composite() {
        String dsl = schema("postgres", "model Product {\n    id: UUID @id @auto\n    sku: String @index\n    @index([sku])\n}\n");
        expectAnalysisError(dsl, "Redundant duplicate index");
    }

    public static void T2_03_reject_duplicate_composite_declarations() {
        expectAnalysisError(orderModel("    @index([status, createdAt])\n    @index([status, createdAt], name = \"idx_other\")\n"),
                "Redundant duplicate index");
    }

    public static void T2_04_reject_same_field_twice_in_index() {
        expectAnalysisError(orderModel("    @index([status, status])\n"), "more than once");
    }

    public static void T2_05_reject_text_field_index() {
        String dsl = schema("postgres", "model Post {\n    id: UUID @id @auto\n    body: String @text @index\n}\n");
        expectAnalysisError(dsl, "unindexable type");
    }

    public static void T2_06_reject_collection_relation_index() {
        String dsl = schema("postgres",
                "model Author {\n    id: UUID @id @auto\n    name: String\n    relations {\n        books: List<Book> @relation(oneToMany)\n    }\n    @index([books])\n}\n\n"
                        + "model Book {\n    id: UUID @id @auto\n    title: String\n    relations {\n        author: Author @relation(manyToOne)\n    }\n}\n");
        expectAnalysisError(dsl, "cannot index collection relation");
    }

    public static void T2_07_reject_invalid_identifier_name() {
        expectAnalysisError(orderModel("    @index([status], name = \"idx-bad name\")\n"), "not a valid SQL identifier");
    }

    public static void T2_08_reject_explicit_name_over_63_chars() {
        String longName = "idx_" + "x".repeat(70);
        expectAnalysisError(orderModel("    @index([status], name = \"" + longName + "\")\n"), "limited to 63");
    }

    public static void T2_09_reject_duplicate_index_names_across_models() {
        String dsl = schema("postgres",
                "model A {\n    id: UUID @id @auto\n    code: String @index(name = \"idx_shared\")\n}\n\n"
                        + "model B {\n    id: UUID @id @auto\n    code: String @index(name = \"idx_shared\")\n}\n");
        expectAnalysisError(dsl, "declared more than once");
    }

    public static void T2_10_primary_key_index_silently_normalized() {
        String dsl = schema("postgres", "model Product {\n    id: UUID @id @auto @index\n    sku: String\n}\n");
        String out = sql(dsl);
        assertNotContains(out, "idx_products_id", "no redundant PK index");
    }

    public static void T2_11_allow_many_to_one_relation_in_index() {
        analyze(orderModel("    @index([customer, status])\n")); // must not throw
    }

    public static void T2_12_reject_unsupported_field_annotation_with_suggestion() {
        String dsl1 = schema("postgres", "model Order {\n    id: UUID @id @auto\n    orderNumber: String @uniquesz\n}\n");
        expectAnalysisError(dsl1, "Did you mean '@unique'?");

        String dsl2 = schema("postgres", "model Order {\n    id: UUID @id @auto\n    createdAt: DateTime @autoNows\n}\n");
        expectAnalysisError(dsl2, "Did you mean '@autoNow'?");
    }

    public static void T2_13_jwt_ergonomic_secret_and_duration_options() {
        // Option 2A: Zero-config (omitted jwtSecret & jwtExpiration) -> auto-derived 64-char secret & 24h (86400000ms)
        String dslAuto = "config {\n    app: \"ShopSphere\"\n    package: \"com.shopsphere.backend\"\n    auth: jwt\n}\n"
                + "model User {\n    id: UUID @id @auto\n    email: String @email @unique\n    password: String @password\n}\n";
        ZenithFile fAuto = analyze(dslAuto);
        assertEquals(64, fAuto.getConfig().getJwtSecret().length(), "auto-derived 64-char hex secret");
        assertEquals(86_400_000L, fAuto.getConfig().getJwtExpirationMs(), "default 24h in ms");

        // Option 2B: Human-readable passphrase (< 32 chars) + unquoted duration literal (24h, 30m, 7d)
        String dslFriendly = "config {\n    app: \"ShopSphere\"\n    auth: jwt\n    jwtSecret: \"shopsphere-dev-key\"\n    jwtExpiration: 24h\n}\n"
                + "model User {\n    id: UUID @id @auto\n    email: String @email @unique\n    password: String @password\n}\n";
        ZenithFile fFriendly = analyze(dslFriendly);
        assertEquals(64, fFriendly.getConfig().getJwtSecret().length(), "short passphrase stretched via SHA-256 to 64 hex chars");
        assertEquals(86_400_000L, fFriendly.getConfig().getJwtExpirationMs(), "24h parsed to 86400000ms");

        // Option 1 (preserved): Explicit 64-char secret + raw milliseconds (30000)
        String dslRaw = "config {\n    app: \"ShopSphere\"\n    auth: jwt\n"
                + "    jwtSecret: \"404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970\"\n"
                + "    jwtExpiration: 30000\n}\n"
                + "model User {\n    id: UUID @id @auto\n    email: String @email @unique\n    password: String @password\n}\n";
        ZenithFile fRaw = analyze(dslRaw);
        assertEquals("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970", fRaw.getConfig().getJwtSecret(), "explicit 64-char secret preserved");
        assertEquals(30_000L, fRaw.getConfig().getJwtExpirationMs(), "raw 30000ms preserved");
    }

    // ------------------------------------------------------------------ T3: Flyway SQL

    public static void T3_01_postgres_composite_index_ddl() {
        String out = sql(orderModel("    @index([customer, status])\n"));
        assertContains(out, "CREATE INDEX IF NOT EXISTS idx_orders_customer_id_status ON orders (customer_id, status);",
                "composite DDL with FK column resolution");
    }

    public static void T3_02_inline_index_ddl_snake_case() {
        String out = sql(orderModel("").replace("orderNumber: String", "orderNumber: String @index"));
        assertContains(out, "CREATE INDEX IF NOT EXISTS idx_orders_order_number ON orders (order_number);", "inline DDL");
    }

    public static void T3_03_unique_index_ddl() {
        String out = sql(orderModel("    @uniqueIndex([orderNumber, customer])\n"));
        assertContains(out, "CREATE UNIQUE INDEX IF NOT EXISTS uidx_orders_order_number_customer_id ON orders (order_number, customer_id);",
                "unique DDL");
    }

    public static void T3_04_custom_name_ddl() {
        String out = sql(orderModel("    @index([status, totalAmount], name = \"idx_order_status_total\")\n"));
        assertContains(out, "CREATE INDEX IF NOT EXISTS idx_order_status_total ON orders (status, total_amount);", "custom name DDL");
    }

    public static void T3_05_mysql_omits_if_not_exists() {
        String out = sql(orderModel("    @index([status, createdAt])\n").replace("database: postgres", "database: mysql"));
        assertContains(out, "CREATE INDEX idx_orders_status_created_at ON orders (status, created_at);", "MySQL DDL");
        assertNotContains(out, "IF NOT EXISTS idx_", "MySQL 8 does not support CREATE INDEX IF NOT EXISTS");
    }

    public static void T3_06_long_name_truncated_with_hash() {
        String dsl = schema("postgres",
                "model VeryLongEnterpriseAccountingLedgerEntry {\n    id: UUID @id @auto\n"
                        + "    organizationIdentifierCode: String\n    fiscalPeriodReferenceNumber: String\n"
                        + "    @index([organizationIdentifierCode, fiscalPeriodReferenceNumber])\n}\n");
        String out1 = sql(dsl);
        String out2 = sql(dsl);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("CREATE INDEX IF NOT EXISTS (\\S+) ON very_long").matcher(out1);
        assertTrue(m.find(), "index statement present");
        String name = m.group(1);
        assertTrue(name.length() <= 63, "name within 63 chars: " + name + " (" + name.length() + ")");
        assertMatches(name, "_[0-9a-f]{8}$", "hash suffix");
        assertEquals(out1, out2, "deterministic output");
    }

    public static void T3_07_reserved_column_is_quoted() {
        String dsl = schema("postgres", "model Setting {\n    id: UUID @id @auto\n    value: String @index\n}\n");
        assertContains(sql(dsl), "ON settings (\"value\");", "reserved word quoted");
    }

    public static void T3_08_no_duplicate_with_auto_fk_index() {
        String out = sql(orderModel("    @index([customer])\n"));
        int count = out.split("ON orders \\(customer_id\\)", -1).length - 1;
        assertEquals(1, count, "customer_id indexed exactly once");
    }

    public static void T3_09_existing_auto_indexes_preserved() {
        String out = sql(orderModel(""));
        assertContains(out, "CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders (customer_id);", "auto FK index still generated");
    }

    // ------------------------------------------------------------------ T4: JPA entity

    public static void T4_01_entity_table_contains_index_annotations() {
        String entity = source(generateAllSources(orderModel("    @index([customer, status])\n")), "entity/Order.java");
        assertContains(entity, "@Table(name = \"orders\", indexes = {", "indexes attribute");
        assertContains(entity, "@Index(name = \"idx_orders_customer_id_status\", columnList = \"customer_id, status\")", "@Index entry");
    }

    public static void T4_02_entity_unique_index_flag() {
        String entity = source(generateAllSources(orderModel("    @uniqueIndex([orderNumber, customer])\n")), "entity/Order.java");
        assertContains(entity, "columnList = \"order_number, customer_id\", unique = true)", "unique = true");
    }

    public static void T4_03_entity_multiple_indexes_comma_separated() {
        String entity = source(generateAllSources(orderModel("    @index([status, createdAt])\n    @index([totalAmount], name = \"idx_total\")\n")),
                "entity/Order.java");
        assertContains(entity, "columnList = \"status, created_at\"),", "first entry followed by comma");
        assertContains(entity, "@Index(name = \"idx_total\", columnList = \"total_amount\")\n})", "last entry closes array");
    }

    public static void T4_04_entity_without_indexes_unchanged() {
        String entity = source(generateAllSources(orderModel("")), "entity/Order.java");
        assertContains(entity, "@Table(name = \"orders\")\n", "plain @Table when no indexes declared");
    }

    public static void T4_05_entity_and_flyway_names_match() {
        String dsl = orderModel("    @index([status, createdAt])\n");
        List<JavaSourceFile> files = generateAllSources(dsl);
        String entity = source(files, "entity/Order.java");
        String ddl = source(files, "V1__initial_schema.sql");
        assertContains(entity, "idx_orders_status_created_at", "entity name");
        assertContains(ddl, "idx_orders_status_created_at", "flyway name");
    }
}

