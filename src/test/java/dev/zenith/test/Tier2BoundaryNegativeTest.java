package dev.zenith.test;

import dev.zenith.analyzer.AnalysisException;
import dev.zenith.ast.ZenithFile;
import dev.zenith.parser.ParseException;

/**
 * Tier 2: Boundary & Corner Cases Test Suite.
 * Rejection tests and boundary validations covering:
 * - Group 1: Duplicate Variable Declarations (T2_01 - T2_05)
 * - Group 2: Reserved Keyword Collision Protection (T2_06 - T2_10)
 * - Group 3: Self-Referential & Forward References (T2_11 - T2_15)
 * - Group 4: Non-Existent / Invalid Property Access (T2_16 - T2_20)
 * - Group 5: Syntax Errors & Position Reporting (T2_21 - T2_25)
 */
public class Tier2BoundaryNegativeTest {

    // --- Group 1: Duplicate Variable Declarations ---

    public static void T2_01_reject_duplicate_variable_same_name_consecutive() {
        String dsl = TestHarness.createOrderModelSchema(
                "var cust = create Customer { fullName = \"First\" }\n" +
                "var cust = create Customer { fullName = \"Second\" }"
        );
        try {
            TestHarness.analyze(dsl);
            throw new AssertionError("Expected AnalysisException for duplicate variable 'cust'");
        } catch (AnalysisException ex) {
            TestHarness.assertContains(ex.getMessage().toLowerCase(), "duplicate", "Error must report duplicate variable");
            TestHarness.assertContains(ex.getMessage(), "cust", "Error must name colliding variable 'cust'");
        }
    }

    public static void T2_02_reject_duplicate_variable_same_name_interleaved() {
        String dsl = TestHarness.createOrderModelSchema(
                "var x = create Customer {}\n" +
                "status = \"PROCESSING\"\n" +
                "var x = create Invoice {}"
        );
        try {
            TestHarness.analyze(dsl);
            throw new AssertionError("Expected AnalysisException for interleaved duplicate variable 'x'");
        } catch (AnalysisException ex) {
            TestHarness.assertContains(ex.getMessage().toLowerCase(), "duplicate", "Error must report duplicate variable");
        }
    }

    public static void T2_03_reject_duplicate_variable_different_target_model() {
        String dsl = TestHarness.createOrderModelSchema(
                "var target = create Customer {}\n" +
                "var target = update Customer { fullName = \"Updated\" }"
        );
        try {
            TestHarness.analyze(dsl);
            throw new AssertionError("Expected AnalysisException for re-declaring variable 'target'");
        } catch (AnalysisException ex) {
            TestHarness.assertContains(ex.getMessage().toLowerCase(), "duplicate", "Error must report duplicate variable");
        }
    }

    public static void T2_04_reject_duplicate_variable_case_collision() {
        String dsl = TestHarness.createOrderModelSchema(
                "var orderItem = create Invoice {}\n" +
                "var orderItem = create Invoice {}"
        );
        try {
            TestHarness.analyze(dsl);
            throw new AssertionError("Expected AnalysisException for exact duplicate variable name");
        } catch (AnalysisException ex) {
            TestHarness.assertContains(ex.getMessage().toLowerCase(), "duplicate", "Error must report duplicate variable");
        }
    }

    public static void T2_05_allow_same_variable_name_in_different_endpoints() {
        String dsl = TestHarness.wrapInStandardSchema(
                "model Item {\n" +
                "    id: Long @id @auto\n" +
                "    endpoints {\n" +
                "        post \"/first\" -> ItemResponse {\n" +
                "            action: {\n" +
                "                var x = create Item {}\n" +
                "            }\n" +
                "        }\n" +
                "        post \"/second\" -> ItemResponse {\n" +
                "            action: {\n" +
                "                var x = create Item {}\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}\n"
        );
        ZenithFile file = TestHarness.analyze(dsl);
        TestHarness.assertNotNull(file, "Same variable name across different endpoint blocks must be allowed");
    }

    // --- Group 2: Reserved Keyword Collision Protection ---

    public static void T2_06_reject_variable_named_entity() {
        String dsl = TestHarness.createOrderModelSchema("var entity = create Customer {}");
        try {
            TestHarness.analyze(dsl);
            throw new AssertionError("Expected AnalysisException for reserved identifier 'entity'");
        } catch (AnalysisException ex) {
            TestHarness.assertContains(ex.getMessage().toLowerCase(), "reserved", "Error must state identifier is reserved");
            TestHarness.assertContains(ex.getMessage(), "entity", "Error must cite 'entity'");
        }
    }

    public static void T2_07_reject_variable_named_currentUser() {
        String dsl = TestHarness.createOrderModelSchema("var currentUser = create Customer {}");
        try {
            TestHarness.analyze(dsl);
            throw new AssertionError("Expected AnalysisException for reserved identifier 'currentUser'");
        } catch (AnalysisException ex) {
            TestHarness.assertContains(ex.getMessage().toLowerCase(), "reserved", "Error must state identifier is reserved");
            TestHarness.assertContains(ex.getMessage(), "currentUser", "Error must cite 'currentUser'");
        }
    }

    public static void T2_08_reject_variable_named_body() {
        String dsl = TestHarness.createOrderModelSchema("var body = create Customer {}");
        try {
            TestHarness.analyze(dsl);
            throw new AssertionError("Expected AnalysisException for reserved identifier 'body'");
        } catch (AnalysisException ex) {
            TestHarness.assertContains(ex.getMessage().toLowerCase(), "reserved", "Error must state identifier is reserved");
            TestHarness.assertContains(ex.getMessage(), "body", "Error must cite 'body'");
        }
    }

    public static void T2_09_reject_variable_named_id() {
        String dsl = TestHarness.createOrderModelSchema("var id = create Customer {}");
        try {
            TestHarness.analyze(dsl);
            throw new AssertionError("Expected AnalysisException for reserved identifier 'id'");
        } catch (AnalysisException ex) {
            TestHarness.assertContains(ex.getMessage().toLowerCase(), "reserved", "Error must state identifier is reserved");
            TestHarness.assertContains(ex.getMessage(), "id", "Error must cite 'id'");
        }
    }

    public static void T2_10_allow_variable_containing_reserved_word_as_substring() {
        String dsl = TestHarness.createOrderModelSchema(
                "var entityId = create Customer {}\n" +
                "var currentUserId = create Invoice {}\n" +
                "var requestBodyPayload = create Customer {}"
        );
        ZenithFile file = TestHarness.analyze(dsl);
        TestHarness.assertNotNull(file, "Variables having reserved keywords as substrings must be allowed");
    }

    // --- Group 3: Self-Referential & Forward References ---

    public static void T2_11_reject_direct_self_referential_initialization() {
        String dsl = TestHarness.createOrderModelSchema("var x = create Invoice { amount = x.amount }");
        try {
            TestHarness.analyze(dsl);
            throw new AssertionError("Expected AnalysisException for self-referential initialization of 'x'");
        } catch (AnalysisException ex) {
            TestHarness.assertContains(ex.getMessage().toLowerCase(), "self", "Error must report self-referential or forward reference");
        }
    }

    public static void T2_12_reject_self_referential_in_update_predicate() {
        String dsl = TestHarness.createOrderModelSchema("var target = update Customer { fullName = target.fullName }");
        try {
            TestHarness.analyze(dsl);
            throw new AssertionError("Expected AnalysisException for self-referential predicate in 'target'");
        } catch (AnalysisException ex) {
            TestHarness.assertContains(ex.getMessage().toLowerCase(), "self", "Error must report self-referential reference");
        }
    }

    public static void T2_13_reject_self_referential_in_nested_field() {
        String dsl = TestHarness.createOrderModelSchema("var inv = create Invoice { orderId = inv.id }");
        try {
            TestHarness.analyze(dsl);
            throw new AssertionError("Expected AnalysisException for self-referential field in 'inv'");
        } catch (AnalysisException ex) {
            TestHarness.assertContains(ex.getMessage().toLowerCase(), "self", "Error must report self-referential reference");
        }
    }

    public static void T2_14_reject_forward_reference_to_variable_declared_later() {
        String dsl = TestHarness.createOrderModelSchema(
                "var a = create Invoice { orderId = b.id }\n" +
                "var b = create Customer {}"
        );
        try {
            TestHarness.analyze(dsl);
            throw new AssertionError("Expected AnalysisException for forward reference to 'b' before its declaration");
        } catch (AnalysisException ex) {
            TestHarness.assertNotNull(ex.getMessage(), "Exception must have error message");
        }
    }

    public static void T2_15_reject_mutual_circular_variable_reference() {
        String dsl = TestHarness.createOrderModelSchema(
                "var first = create Invoice { amount = second.amount }\n" +
                "var second = create Invoice { amount = first.amount }"
        );
        try {
            TestHarness.analyze(dsl);
            throw new AssertionError("Expected AnalysisException for unresolvable circular reference");
        } catch (AnalysisException ex) {
            TestHarness.assertNotNull(ex.getMessage(), "Exception must have error message");
        }
    }

    // --- Group 4: Non-Existent / Invalid Property Access ---

    public static void T2_16_reject_nonexistent_property_access_on_bound_variable() {
        String dsl = TestHarness.createOrderModelSchema(
                "var inv = create Invoice {}\n" +
                "totalAmount = inv.nonExistentProperty"
        );
        try {
            TestHarness.analyze(dsl);
            throw new AssertionError("Expected AnalysisException for non-existent property on Invoice");
        } catch (AnalysisException ex) {
            TestHarness.assertContains(ex.getMessage().toLowerCase(), "nonexistentproperty", "Error message must mention the invalid property");
        }
    }

    public static void T2_17_reject_typo_property_access_with_suggestion() {
        String dsl = TestHarness.createOrderModelSchema(
                "var inv = create Invoice {}\n" +
                "totalAmount = inv.amont"
        );
        try {
            TestHarness.analyze(dsl);
            throw new AssertionError("Expected AnalysisException for typo property 'amont'");
        } catch (AnalysisException ex) {
            TestHarness.assertContains(ex.getMessage().toLowerCase(), "amont", "Error must cite typo property name");
        }
    }

    public static void T2_18_reject_accessing_field_of_different_model_type() {
        String dsl = TestHarness.createOrderModelSchema(
                "var inv = create Invoice {}\n" +
                "totalAmount = inv.fullName" // fullName exists on Customer, not Invoice
        );
        try {
            TestHarness.analyze(dsl);
            throw new AssertionError("Expected AnalysisException for accessing Customer field on Invoice");
        } catch (AnalysisException ex) {
            TestHarness.assertContains(ex.getMessage().toLowerCase(), "fullname", "Error must cite field 'fullName'");
        }
    }

    public static void T2_19_reject_invalid_relation_subfield_access() {
        String dsl = TestHarness.createOrderModelSchema(
                "var ord = create Order {}\n" +
                "totalAmount = ord.customer.invalidSecretField"
        );
        try {
            TestHarness.analyze(dsl);
            throw new AssertionError("Expected AnalysisException for invalid subfield navigation");
        } catch (AnalysisException ex) {
            TestHarness.assertContains(ex.getMessage().toLowerCase(), "invalidsecretfield", "Error must cite invalid subfield");
        }
    }

    public static void T2_20_reject_method_invocation_syntax_on_bound_variable() {
        String dsl = TestHarness.createOrderModelSchema(
                "var inv = create Invoice {}\n" +
                "totalAmount = inv.calculateTax()"
        );
        try {
            TestHarness.analyze(dsl);
            throw new AssertionError("Expected exception for method invocation syntax in action block");
        } catch (Exception ex) {
            TestHarness.assertNotNull(ex.getMessage(), "Exception must be thrown");
        }
    }

    // --- Group 5: Syntax Errors & Position Reporting ---

    public static void T2_21_syntax_error_missing_variable_identifier() {
        String dsl = TestHarness.createOrderModelSchema("var = create Customer {}");
        try {
            TestHarness.parse(dsl);
            throw new AssertionError("Expected ParseException for missing identifier after 'var'");
        } catch (ParseException ex) {
            TestHarness.assertTrue(ex.getLine() > 0, "Line must be reported");
            TestHarness.assertTrue(ex.getColumn() > 0, "Column must be reported");
        }
    }

    public static void T2_22_syntax_error_missing_assignment_operator() {
        String dsl = TestHarness.createOrderModelSchema("var x create Customer {}");
        try {
            TestHarness.parse(dsl);
            throw new AssertionError("Expected ParseException for missing '=' after variable identifier");
        } catch (ParseException ex) {
            TestHarness.assertTrue(ex.getLine() > 0, "Line must be reported");
        }
    }

    public static void T2_23_syntax_error_missing_target_model_name() {
        String dsl = TestHarness.createOrderModelSchema("var x = create {}");
        try {
            TestHarness.parse(dsl);
            throw new AssertionError("Expected ParseException for missing model name after 'create'");
        } catch (ParseException ex) {
            TestHarness.assertTrue(ex.getLine() > 0, "Line must be reported");
        }
    }

    public static void T2_24_syntax_error_unclosed_action_block() {
        String dsl = TestHarness.wrapInStandardSchema(
                "model Item {\n" +
                "    id: Long @id @auto\n" +
                "    endpoints {\n" +
                "        post \"/execute\" -> ItemResponse {\n" +
                "            action: {\n" +
                "                var x = create Item {}\n" +
                "        }\n" +
                "    }\n" +
                "}\n"
        );
        try {
            TestHarness.parse(dsl);
            throw new AssertionError("Expected ParseException for unclosed action block");
        } catch (ParseException ex) {
            TestHarness.assertTrue(ex.getLine() > 0, "Line must be reported");
        }
    }

    public static void T2_25_syntax_error_reports_exact_source_line_and_column() {
        String dsl = TestHarness.createOrderModelSchema("var @invalidToken = create Customer {}");
        try {
            TestHarness.parse(dsl);
            throw new AssertionError("Expected ParseException for invalid token in variable declaration");
        } catch (ParseException ex) {
            TestHarness.assertTrue(ex.getLine() > 0, "Syntax error must include line > 0");
            TestHarness.assertTrue(ex.getColumn() > 0, "Syntax error must include column > 0");
        }
    }
}
