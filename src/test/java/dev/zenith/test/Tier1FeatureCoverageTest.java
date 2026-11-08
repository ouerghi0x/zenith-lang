package dev.zenith.test;

import dev.zenith.analyzer.AnalysisException;
import dev.zenith.ast.*;
import dev.zenith.lexer.Token;
import dev.zenith.lexer.TokenType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Tier 1: Feature Coverage Test Suite.
 * Comprehensive happy-path coverage across all core capabilities of Feature #1:
 * - Group 1: Lexer & Token Recognition (T1_01 - T1_05)
 * - Group 2: Grammar & AST Instantiation (T1_06 - T1_10)
 * - Group 3: Scoping & Type Inference (T1_11 - T1_15)
 * - Group 4: Property Resolution & DB-Generated Fields (T1_16 - T1_20)
 * - Group 5: Transpilation & Repository Auto-Injection (T1_21 - T1_25)
 * - Group 6: Transactional Boundary & Method Semantics (T1_26 - T1_30)
 */
public class Tier1FeatureCoverageTest {

    // --- Group 1: Lexer & Keyword Recognition ---

    public static void T1_01_lexer_recognizes_var_keyword() {
        List<Token> tokens = TestHarness.tokenize("var x = 10");
        TestHarness.assertTrue(!tokens.isEmpty(), "Tokens should not be empty");
        Token first = tokens.get(0);
        TestHarness.assertEquals("var", first.getValue(), "First token value should be 'var'");
        // Check either TokenType.VAR enum or token type name
        TestHarness.assertEquals("VAR", first.getType().name(), "Token type should be VAR");
    }

    public static void T1_02_lexer_var_token_case_sensitivity() {
        List<Token> tokens = TestHarness.tokenize("Var VAR var");
        TestHarness.assertTrue(tokens.size() >= 3, "Expected at least 3 tokens");
        TestHarness.assertEquals("IDENTIFIER", tokens.get(0).getType().name(), "'Var' should be IDENTIFIER");
        TestHarness.assertEquals("IDENTIFIER", tokens.get(1).getType().name(), "'VAR' should be IDENTIFIER");
        TestHarness.assertEquals("VAR", tokens.get(2).getType().name(), "'var' should be keyword VAR");
    }

    public static void T1_03_lexer_allows_var_as_field_name_backward_compatibility() {
        String dsl = TestHarness.wrapInStandardSchema(
                "model ConfigItem {\n" +
                "    id: Long @id @auto\n" +
                "    var: String\n" +
                "}\n"
        );
        ZenithFile file = TestHarness.parse(dsl);
        TestHarness.assertNotNull(file, "Parsed file should not be null");
        ModelDef model = file.getModels().get(0);
        TestHarness.assertNotNull(model.findField("var"), "Field 'var' must be successfully parsed on model");
    }

    public static void T1_04_lexer_var_inside_string_literals_not_tokenized_as_keyword() {
        List<Token> tokens = TestHarness.tokenize("\"var inside string\"");
        TestHarness.assertTrue(!tokens.isEmpty(), "Tokens should not be empty");
        TestHarness.assertEquals("STRING_LITERAL", tokens.get(0).getType().name(), "Should be STRING_LITERAL");
        TestHarness.assertEquals("var inside string", tokens.get(0).getValue(), "String literal value should be preserved");
    }

    public static void T1_05_lexer_multiple_consecutive_var_tokens() {
        List<Token> tokens = TestHarness.tokenize("var var");
        TestHarness.assertTrue(tokens.size() >= 2, "Expected 2 tokens");
        TestHarness.assertEquals("VAR", tokens.get(0).getType().name(), "First should be VAR");
        TestHarness.assertEquals("VAR", tokens.get(1).getType().name(), "Second should be VAR");
    }

    // --- Group 2: Grammar & AST Instantiation ---

    public static void T1_06_parser_creates_variable_binding_create_action() {
        String dsl = TestHarness.createOrderModelSchema("var newCust = create Customer {}");
        ZenithFile file = TestHarness.parse(dsl);
        ModelDef order = file.getModels().stream().filter(m -> m.getName().equals("Order")).findFirst().orElse(null);
        TestHarness.assertNotNull(order, "Order model should exist");
        CustomEndpointDef endpoint = order.getCustomEndpoints().get(0);
        TestHarness.assertTrue(TestHarness.hasVariableBindingAST(endpoint, "newCust", "create", "Customer"),
                "Endpoint must contain VariableBindingActionDef for 'newCust'");
    }

    public static void T1_07_parser_creates_variable_binding_update_action() {
        String dsl = TestHarness.createOrderModelSchema("var updCust = update Customer { fullName = \"Updated Customer\" }");
        ZenithFile file = TestHarness.parse(dsl);
        ModelDef order = file.getModels().stream().filter(m -> m.getName().equals("Order")).findFirst().orElse(null);
        TestHarness.assertNotNull(order, "Order model should exist");
        CustomEndpointDef endpoint = order.getCustomEndpoints().get(0);
        TestHarness.assertTrue(TestHarness.hasVariableBindingAST(endpoint, "updCust", "update", "Customer"),
                "Endpoint must contain VariableBindingActionDef for 'updCust'");
    }

    public static void T1_08_parser_variable_binding_with_field_assignments() {
        String dsl = TestHarness.createOrderModelSchema(
                "var newInv = create Invoice {\n" +
                "    orderId = id\n" +
                "    amount = 150.00\n" +
                "    status = \"ISSUED\"\n" +
                "}"
        );
        ZenithFile file = TestHarness.parse(dsl);
        ModelDef order = file.getModels().stream().filter(m -> m.getName().equals("Order")).findFirst().orElse(null);
        CustomEndpointDef endpoint = order.getCustomEndpoints().get(0);
        TestHarness.assertTrue(TestHarness.hasVariableBindingAST(endpoint, "newInv", "create", "Invoice"),
                "Endpoint must parse variable binding with field assignments");
    }

    public static void T1_09_parser_preserves_action_statement_order() {
        String dsl = TestHarness.createOrderModelSchema(
                "status = \"PROCESSING\"\n" +
                "var inv = create Invoice { amount = totalAmount }\n" +
                "status = \"COMPLETED\""
        );
        ZenithFile file = TestHarness.parse(dsl);
        ModelDef order = file.getModels().stream().filter(m -> m.getName().equals("Order")).findFirst().orElse(null);
        CustomEndpointDef endpoint = order.getCustomEndpoints().get(0);
        List<String> rawStmts = endpoint.getActionStatements();
        TestHarness.assertTrue(rawStmts.size() >= 3, "Expected at least 3 action statements in exact order");
        TestHarness.assertContains(rawStmts.get(0), "PROCESSING", "First statement must be PROCESSING");
        TestHarness.assertContains(rawStmts.get(1), "inv", "Second statement must be variable binding");
        TestHarness.assertContains(rawStmts.get(2), "COMPLETED", "Third statement must be COMPLETED");
    }

    public static void T1_10_parser_variable_binding_metadata_line_column() {
        String dsl = TestHarness.createOrderModelSchema("var testBinding = create Customer {}");
        ZenithFile file = TestHarness.parse(dsl);
        ModelDef order = file.getModels().stream().filter(m -> m.getName().equals("Order")).findFirst().orElse(null);
        CustomEndpointDef endpoint = order.getCustomEndpoints().get(0);
        TestHarness.assertTrue(endpoint.getSourceLine() > 0, "Source line must be recorded");
    }

    // --- Group 3: Scoping & Type Inference ---

    public static void T1_11_semantics_infers_model_type_from_create_expression() {
        String dsl = TestHarness.createOrderModelSchema("var inv = create Invoice { amount = totalAmount }");
        ZenithFile file = TestHarness.analyze(dsl);
        TestHarness.assertNotNull(file, "Semantic analysis should succeed and infer Invoice type");
    }

    public static void T1_12_semantics_infers_model_type_from_update_expression() {
        String dsl = TestHarness.createOrderModelSchema("var c = update Customer { fullName = \"Updated\" }");
        ZenithFile file = TestHarness.analyze(dsl);
        TestHarness.assertNotNull(file, "Semantic analysis should infer Customer type for update");
    }

    public static void T1_13_semantics_registers_variable_in_action_symbol_table() {
        String dsl = TestHarness.createOrderModelSchema(
                "var inv = create Invoice { amount = totalAmount }\n" +
                "totalAmount = inv.amount"
        );
        ZenithFile file = TestHarness.analyze(dsl);
        TestHarness.assertNotNull(file, "Variable 'inv' must be registered in action symbol table");
    }

    public static void T1_14_semantics_variable_visible_to_subsequent_statements() {
        String dsl = TestHarness.createOrderModelSchema(
                "var cust = create Customer { fullName = \"John Doe\", email = \"john@test.com\" }\n" +
                "customerId = cust.id"
        );
        ZenithFile file = TestHarness.analyze(dsl);
        TestHarness.assertNotNull(file, "Subsequent statement must access 'cust.id'");
    }

    public static void T1_15_semantics_multiple_distinct_variables_different_types() {
        String dsl = TestHarness.createOrderModelSchema(
                "var cust = create Customer { fullName = \"Alice\", email = \"alice@test.com\" }\n" +
                "var inv = create Invoice { amount = totalAmount, orderId = id }"
        );
        ZenithFile file = TestHarness.analyze(dsl);
        TestHarness.assertNotNull(file, "Multiple variables of distinct types should analyze cleanly");
    }

    // --- Group 4: Property Resolution & DB-Generated Fields ---

    public static void T1_16_semantics_resolves_declared_model_field() {
        String dsl = TestHarness.createOrderModelSchema(
                "var inv = create Invoice { amount = 50.00 }\n" +
                "totalAmount = inv.amount"
        );
        ZenithFile file = TestHarness.analyze(dsl);
        TestHarness.assertNotNull(file, "Property 'amount' should resolve to Invoice.amount");
    }

    public static void T1_17_semantics_resolves_db_generated_id_field() {
        String dsl = TestHarness.createOrderModelSchema(
                "var cust = create Customer { fullName = \"Bob\", email = \"bob@test.com\" }\n" +
                "customerId = cust.id"
        );
        ZenithFile file = TestHarness.analyze(dsl);
        TestHarness.assertNotNull(file, "DB-generated 'id' field must be resolved on bound variable");
    }

    public static void T1_18_semantics_resolves_db_generated_created_at_field() {
        String dsl = TestHarness.createOrderModelSchema(
                "var cust = create Customer { fullName = \"Carol\", email = \"carol@test.com\" }\n" +
                "createdAt = cust.createdAt"
        );
        ZenithFile file = TestHarness.analyze(dsl);
        TestHarness.assertNotNull(file, "DB-generated 'createdAt' must be resolved on bound variable");
    }

    public static void T1_19_semantics_resolves_db_generated_updated_at_field() {
        String dsl = TestHarness.createOrderModelSchema(
                "var cust = create Customer { fullName = \"Dave\", email = \"dave@test.com\" }\n" +
                "updatedAt = cust.updatedAt"
        );
        ZenithFile file = TestHarness.analyze(dsl);
        TestHarness.assertNotNull(file, "DB-generated 'updatedAt' must be resolved on bound variable");
    }

    public static void T1_20_semantics_resolves_relation_navigation_field() {
        String dsl = TestHarness.createOrderModelSchema(
                "var ord = create Order { totalAmount = 250.00 }\n" +
                "customerId = ord.customer.id"
        );
        ZenithFile file = TestHarness.analyze(dsl);
        TestHarness.assertNotNull(file, "Relation navigation 'ord.customer.id' must be resolved");
    }

    // --- Group 5: Transpilation & Repository Auto-Injection ---

    public static void T1_21_codegen_transpiles_var_create_to_java_save_assignment() {
        String dsl = TestHarness.createOrderModelSchema("var newCust = create Customer { fullName = \"Eve\", email = \"eve@test.com\" }");
        String serviceCode = TestHarness.generateServiceImplementation(dsl, "Order");
        TestHarness.assertContains(serviceCode, "Customer newCust =", "Generated code must declare and assign 'Customer newCust'");
        TestHarness.assertContains(serviceCode, "customerRepository.save(", "Generated code must invoke customerRepository.save(...)");
    }

    public static void T1_22_codegen_transpiles_var_update_to_java_find_and_save() {
        String dsl = TestHarness.createOrderModelSchema("var updCust = update Customer { fullName = \"Updated Eve\" }");
        String serviceCode = TestHarness.generateServiceImplementation(dsl, "Order");
        TestHarness.assertContains(serviceCode, "Customer updCust =", "Generated code must declare and assign 'Customer updCust'");
        TestHarness.assertContains(serviceCode, "customerRepository.save(", "Generated code must invoke customerRepository.save(...)");
    }

    public static void T1_23_codegen_replaces_bound_property_with_getter() {
        String dsl = TestHarness.createOrderModelSchema(
                "var inv = create Invoice { amount = 75.00 }\n" +
                "totalAmount = inv.amount"
        );
        String serviceCode = TestHarness.generateServiceImplementation(dsl, "Order");
        TestHarness.assertContains(serviceCode, "inv.getAmount()", "Property access 'inv.amount' must be transpiled to 'inv.getAmount()'");
    }

    public static void T1_24_codegen_auto_injects_extra_repositories_into_service() {
        String dsl = TestHarness.createOrderModelSchema("var inv = create Invoice { amount = 80.00 }");
        String serviceCode = TestHarness.generateServiceImplementation(dsl, "Order");
        TestHarness.assertContains(serviceCode, "InvoiceRepository invoiceRepository", "Service must inject InvoiceRepository for bound model");
    }

    public static void T1_25_codegen_transpiles_nested_getter_chain() {
        String dsl = TestHarness.createOrderModelSchema(
                "var ord = create Order { totalAmount = 120.00 }\n" +
                "customerId = ord.customer.id"
        );
        String serviceCode = TestHarness.generateServiceImplementation(dsl, "Order");
        TestHarness.assertContains(serviceCode, "ord.getCustomer().getId()", "Nested property 'ord.customer.id' must be transpiled to 'ord.getCustomer().getId()'");
    }

    // --- Group 6: Transactional Boundary & Method Semantics ---

    public static void T1_26_codegen_annotates_action_with_transactional() {
        String dsl = TestHarness.createOrderModelSchema("var inv = create Invoice { amount = 500.00 }");
        String serviceCode = TestHarness.generateServiceImplementation(dsl, "Order");
        TestHarness.assertContains(serviceCode, "@Transactional", "Custom action method must be annotated with @Transactional");
    }

    public static void T1_27_codegen_ensures_transactional_import_present() {
        String dsl = TestHarness.createOrderModelSchema("var inv = create Invoice { amount = 500.00 }");
        String serviceCode = TestHarness.generateServiceImplementation(dsl, "Order");
        TestHarness.assertContains(serviceCode, "import org.springframework.transaction.annotation.Transactional;",
                "Generated file must import Spring Transactional annotation");
    }

    public static void T1_28_codegen_preserves_execution_sequence_in_java_output() {
        String dsl = TestHarness.createOrderModelSchema(
                "status = \"FIRST\"\n" +
                "var inv = create Invoice { amount = 10.00 }\n" +
                "status = \"SECOND\""
        );
        String serviceCode = TestHarness.generateServiceImplementation(dsl, "Order");
        int idxFirst = serviceCode.indexOf("\"FIRST\"");
        int idxInv = serviceCode.indexOf("new Invoice()");
        int idxSecond = serviceCode.indexOf("\"SECOND\"");
        TestHarness.assertTrue(idxFirst != -1 && idxInv != -1 && idxSecond != -1, "All 3 statements must be present");
        TestHarness.assertTrue(idxFirst < idxInv && idxInv < idxSecond, "Statements must be transpiled in exact sequence order");
    }

    public static void T1_29_codegen_handles_void_return_custom_action() {
        String dsl = TestHarness.wrapInStandardSchema(
                "model Task {\n" +
                "    id: Long @id @auto\n" +
                "    title: String\n" +
                "    endpoints {\n" +
                "        post \"/doWork\" -> Void {\n" +
                "            action: {\n" +
                "                var t = create Task { title = \"Subtask\" }\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}\n"
        );
        String serviceCode = TestHarness.generateServiceImplementation(dsl, "Task");
        TestHarness.assertContains(serviceCode, "public void doWork(", "Method return type should be void");
    }

    public static void T1_30_codegen_handles_dto_return_custom_action() {
        String dsl = TestHarness.wrapInStandardSchema(
                "dto TaskResponse {\n" +
                "    id: Long\n" +
                "    title: String\n" +
                "}\n\n" +
                "model Task {\n" +
                "    id: Long @id @auto\n" +
                "    title: String\n" +
                "    endpoints {\n" +
                "        post \"/createSubtask\" -> TaskResponse {\n" +
                "            action: {\n" +
                "                var sub = create Task { title = \"New Subtask\" }\n" +
                "                return TaskResponse { id = sub.id, title = sub.title }\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}\n"
        );
        String serviceCode = TestHarness.generateServiceImplementation(dsl, "Task");
        TestHarness.assertContains(serviceCode, "TaskResponse", "Service method return type must be TaskResponse");
    }
}
