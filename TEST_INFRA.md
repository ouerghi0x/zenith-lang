# Test Infrastructure & Specification: Zenith Feature #1 (Action Variable Binding)

## 1. Overview & Mission

This document defines the automated test infrastructure and test architecture for **Feature #1: Action Variable Binding (`var x = create / update ...`)** in the Zenith DSL Compiler (`apiRestLanguage`).

The primary objective of the test suite is to verify the correct behavior, boundary resilience, combinatorial stability, and end-to-end code generation of action variable bindings through **opaque-box testing**. Tests treat the compiler as a black box: feeding `.zen` domain-specific specifications and verifying:
1. Lexer tokenization and keyword recognition (`TokenType.VAR`).
2. Parser grammar rules and AST node instantiation (`VariableBindingActionDef`).
3. Semantic analysis scoping, duplicate symbol rejection, reserved keyword protection, self-referential initialization prevention, and property resolution against target models and database-generated fields (`id`, `createdAt`, `updatedAt`).
4. Java service transpilation, Spring Data repository auto-injection (`extraRepos`), getter chain generation, and Spring `@Transactional` method boundaries.
5. End-to-end application code generation and compatibility with Spring Boot 3 / Java 17.

---

## 2. Test Architecture & Directory Layout

The test suite is structured into dedicated, isolated tiers under `src/test/java/dev/zenith/test/`:

```
apiRestLanguage/
├── build-cli.bat                                  # Compiles core compiler into bin\zenith.jar
├── run-tests.bat                                  # Windows CMD runner for compiler & test suite
├── run-tests.ps1                                  # PowerShell runner for cross-platform execution
├── TEST_INFRA.md                                  # This test infrastructure document
├── TEST_READY.md                                  # Published completion signal & summary scorecard
├── src/
│   ├── main/java/dev/zenith/...                   # Core compiler implementation
│   └── test/java/dev/zenith/test/                 # Opaque-box automated test suite
│       ├── TestHarness.java                       # DSL builder, assertion oracle & compilation runner
│       ├── Tier1FeatureCoverageTest.java          # Tier 1: Core feature & functional coverage (30 tests)
│       ├── Tier2BoundaryNegativeTest.java         # Tier 2: Boundary, negative & error reporting (25 tests)
│       ├── Tier3CombinationsTest.java             # Tier 3: Pairwise & chained interactions (10 tests)
│       ├── Tier4RealWorldScenariosTest.java       # Tier 4: Real-world enterprise workflows (5 tests)
│       └── E2ETestSuiteRunner.java                # Test suite runner & CLI diagnostic reporter
└── target/
    ├── classes/                                   # Compiled compiler bytecode
    └── test-classes/                              # Compiled test bytecode
```

### Key Architectural Properties
- **Zero External Dependencies**: The test harness and test runner run directly on standard JDK 17 (`javac` and `java`), matching the lightweight, self-contained architecture of the Zenith compiler.
- **Progressive Testability**: The harness dynamically adapts to compiler stages, verifying parsing, semantic analysis, AST representation, and service generation.
- **Fail-Fast Error Reporting**: Every test case produces clear diagnostic output, reporting the exact mismatch, expected vs actual values, line/column markers, and exception messages.

---

## 3. Runner Invocation Commands

### Standard Test Execution
To build the compiler and execute the complete 4-tier test suite:

```cmd
:: Windows CMD
run-tests.bat
```

```powershell
# Windows PowerShell
.\run-tests.ps1
```

### Targeted Tier Execution
Developers and CI agents can run individual test tiers:

```cmd
:: Run Tier 1 Feature Coverage only
run-tests.bat --tier 1

:: Run Tier 2 Boundary & Negative Tests only
run-tests.bat --tier 2

:: Run Tier 3 Cross-Feature Combinations only
run-tests.bat --tier 3

:: Run Tier 4 Real-World Application Scenarios only
run-tests.bat --tier 4
```

### Behind the Scenes
When `run-tests.bat` is invoked:
1. Calls `build-cli.bat` to compile `src/main/java/*.java` into `target/classes` and update `bin/zenith.jar`.
2. Compiles `src/test/java/dev/zenith/test/*.java` into `target/test-classes` using `javac -cp bin/zenith.jar`.
3. Launches `java -cp "target/test-classes;bin/zenith.jar" dev.zenith.test.E2ETestSuiteRunner %*`.
4. Returns exit code `0` on 100% success, or non-zero on test failures.

---

## 4. Test Methodologies

The test suite applies four formal software testing methodologies to guarantee comprehensive coverage:

### 1. Category-Partition Method
Specifications are partitioned into functional categories:
- **Input Syntax**: `create <Model>`, `update <Model>`, with field assignments, with predicates.
- **Identifier Naming**: Valid camelCase, PascalCase, snake_case, single character, numeric suffix.
- **Variable Scope**: Single action block, multiple actions in same endpoint, multiple endpoints on same model.
- **Target Expressions**: Standalone creation, update-by-id, chained property assignment.

### 2. Boundary Value Analysis (BVA)
Tests probe limits, boundary conditions, and negative thresholds:
- **Identifier Collisions**: Shadowing reserved keywords (`entity`, `currentUser`, `body`, `id`).
- **Scope Multiplicity**: Re-declaring the same variable name within the same action block.
- **Initialization Order**: Self-referential initialization (RHS referencing the declared identifier) and forward references.
- **Type Boundaries**: Accessing non-existent properties, invalid field casing, or relations.
- **Grammar Degradation**: Missing identifiers (`var = create`), missing assignment (`var x create`), unclosed braces.

### 3. Pairwise & Combinatorial Testing
Action variable bindings interact with multiple compiler features:
- **Chained Persistence**: `var a = create A; var b = create B { aRef = a.id }`.
- **Mixed Operations**: `var created = create A; var updated = update B where id == ...`.
- **Interleaved Actions**: Mixing variable declarations with raw field assignments (`status = PAID`), guards (`guard: ...`), and email notifications (`sendEmail(...)`).
- **HTTP Method Matrix**: Verifying variable binding works identically across `POST`, `PUT`, and `PATCH` endpoints.
- **Payload Integration**: Binding entity fields from request payloads (`body.fieldName`).

### 4. Real-World Workload Scenarios
Simulating production enterprise applications with realistic domain models:
- **E-Commerce Checkout & Fulfillment**: Customer checkout creating Order, Invoice, and Payment records within a single atomic `@Transactional` boundary.
- **Multi-Tenant SaaS Onboarding**: Tenant provisioning with initial Administrator Account and audit logging.
- **Banking Ledger & Double-Entry Funds Transfer**: Source and target account lookups, balance adjustments, and audit ledger entry generation.
- **Healthcare Clinical Intake**: Patient creation, medical chart generation, and initial prescription dispatch.
- **Logistics & Supply Chain Dispatch**: Order status update, shipment creation, waybill issuance, and real-time tracking record generation.

---

## 5. Feature Inventory & Test Tier Mapping

| Feature # | Feature Name | Test Tier | Primary Test Class | Description |
|:---:|---|:---:|---|---|
| **1** | `var` Keyword Token | Tier 1 | `Tier1FeatureCoverageTest` | Tokenize `"var"` as `TokenType.VAR`, preserve identifier fallback for legacy schemas |
| **2** | Action Binding Grammar (`create`) | Tier 1 | `Tier1FeatureCoverageTest` | Parse `var <id> = create <Model> { ... }` in action blocks |
| **3** | Action Binding Grammar (`update`) | Tier 1 | `Tier1FeatureCoverageTest` | Parse `var <id> = update <Model> where ... { ... }` in action blocks |
| **4** | `VariableBindingActionDef` AST Node | Tier 1 | `Tier1FeatureCoverageTest` | Verify AST node structure, variable name, action type, target model, expression |
| **5** | Execution Order Preservation | Tier 1 | `Tier1FeatureCoverageTest` | Verify textual sequence of variable bindings and actions is strictly preserved |
| **6** | Action-Level Symbol Table Scoping | Tier 1 | `Tier1FeatureCoverageTest` | Verify local symbol table creation, symbol insertion, and block isolation |
| **7** | Inferred Model Type Registration | Tier 1 | `Tier1FeatureCoverageTest` | Infer target `ModelDef` from RHS expression and associate with variable symbol |
| **8** | Duplicate Declaration Guard | Tier 2 | `Tier2BoundaryNegativeTest` | Reject duplicate declarations of the same variable name within an action block |
| **9** | Reserved Identifier Protection | Tier 2 | `Tier2BoundaryNegativeTest` | Reject variables named `entity`, `currentUser`, `body`, or `id` |
| **10** | Self-Referential Initialization Guard | Tier 2 | `Tier2BoundaryNegativeTest` | Reject RHS expressions referencing the variable being bound |
| **11** | Bound Variable Property Resolution | Tier 1 | `Tier1FeatureCoverageTest` | Validate property access `x.property` against model fields |
| **12** | Database Generated Attribute Access | Tier 1 | `Tier1FeatureCoverageTest` | Validate access to `id`, `createdAt`, `updatedAt` on bound variables |
| **13** | Extra Repository Auto-Injection | Tier 1 | `Tier1FeatureCoverageTest` | Auto-inject Spring Data repositories for models bound in action blocks |
| **14** | Local Variable Transpilation (`create`) | Tier 1 | `Tier1FeatureCoverageTest` | Transpile `create` to Java declaration, setters, and `repository.save(...)` |
| **15** | Local Variable Transpilation (`update`) | Tier 1 | `Tier1FeatureCoverageTest` | Transpile `update` to `findById(...)`, mutation, and `repository.save(...)` |
| **16** | Bound Variable Getter Replacement | Tier 1 | `Tier1FeatureCoverageTest` | Transpile `x.field` to `x.getField()` in Java service code |
| **17** | Nested Property Getter Chaining | Tier 1 | `Tier1FeatureCoverageTest` | Transpile `x.relation.field` to `x.getRelation().getField()` |
| **18** | Spring `@Transactional` Boundary | Tier 1 | `Tier1FeatureCoverageTest` | Ensure multi-entity custom action service methods are annotated `@Transactional` |
| **19** | E2E Test Infrastructure & Harness | All | `TestHarness` | DSL parsing, semantic analysis, code generation assertion utilities |
| **20** | Tier 1 Feature Coverage Tests | Tier 1 | `Tier1FeatureCoverageTest` | Functional tests covering features 1-7, 11-18 (>=5 tests per group) |
| **21** | Tier 2 Boundary & Negative Tests | Tier 2 | `Tier2BoundaryNegativeTest` | Rejection tests for duplicates, reserved words, self-references, typos (>=5 per group) |
| **22** | Tier 3 Cross-Feature Combinations | Tier 3 | `Tier3CombinationsTest` | Chained variable creation, mixed create/update, body payload integration |
| **23** | Tier 4 Real-World Application Scenarios | Tier 4 | `Tier4RealWorldScenariosTest` | Complete enterprise checkout, multi-tenant onboarding, banking ledger |
| **24** | Compiler Build & Packaging Verification | Infra | `build-cli.bat` | Clean compilation and packaging via standard build script |
| **25** | Generated Code Maven Compilation | Tier 4 | `Tier4RealWorldScenariosTest` | Generated Spring Boot services compile cleanly under Java 17 |

---

## 6. Test Suite Detailed Specifications

### Tier 1: Feature Coverage (30 Tests)
- **Group 1: Lexer & Keyword Recognition (5 tests)**
  - `T1_01_lexer_recognizes_var_keyword`
  - `T1_02_lexer_var_token_case_sensitivity`
  - `T1_03_lexer_allows_var_as_field_name_backward_compatibility`
  - `T1_04_lexer_var_inside_string_literals_not_tokenized_as_keyword`
  - `T1_05_lexer_multiple_consecutive_var_tokens`
- **Group 2: Grammar & AST Instantiation (5 tests)**
  - `T1_06_parser_creates_variable_binding_create_action`
  - `T1_07_parser_creates_variable_binding_update_action`
  - `T1_08_parser_variable_binding_with_field_assignments`
  - `T1_09_parser_preserves_action_statement_order`
  - `T1_10_parser_variable_binding_metadata_line_column`
- **Group 3: Scoping & Type Inference (5 tests)**
  - `T1_11_semantics_infers_model_type_from_create_expression`
  - `T1_12_semantics_infers_model_type_from_update_expression`
  - `T1_13_semantics_registers_variable_in_action_symbol_table`
  - `T1_14_semantics_variable_visible_to_subsequent_statements`
  - `T1_15_semantics_multiple_distinct_variables_different_types`
- **Group 4: Property Resolution & DB-Generated Fields (5 tests)**
  - `T1_16_semantics_resolves_declared_model_field`
  - `T1_17_semantics_resolves_db_generated_id_field`
  - `T1_18_semantics_resolves_db_generated_created_at_field`
  - `T1_19_semantics_resolves_db_generated_updated_at_field`
  - `T1_20_semantics_resolves_relation_navigation_field`
- **Group 5: Transpilation & Repository Auto-Injection (5 tests)**
  - `T1_21_codegen_transpiles_var_create_to_java_save_assignment`
  - `T1_22_codegen_transpiles_var_update_to_java_find_and_save`
  - `T1_23_codegen_replaces_bound_property_with_getter`
  - `T1_24_codegen_auto_injects_extra_repositories_into_service`
  - `T1_25_codegen_transpiles_nested_getter_chain`
- **Group 6: Transactional Boundary & Method Semantics (5 tests)**
  - `T1_26_codegen_annotates_action_with_transactional`
  - `T1_27_codegen_ensures_transactional_import_present`
  - `T1_28_codegen_preserves_execution_sequence_in_java_output`
  - `T1_29_codegen_handles_void_return_custom_action`
  - `T1_30_codegen_handles_dto_return_custom_action`

### Tier 2: Boundary & Negative Cases (25 Tests)
- **Group 1: Duplicate Variable Declarations (5 tests)**
  - `T2_01_reject_duplicate_variable_same_name_consecutive`
  - `T2_02_reject_duplicate_variable_same_name_interleaved`
  - `T2_03_reject_duplicate_variable_different_target_model`
  - `T2_04_reject_duplicate_variable_case_collision`
  - `T2_05_allow_same_variable_name_in_different_endpoints`
- **Group 2: Reserved Keyword Collision Protection (5 tests)**
  - `T2_06_reject_variable_named_entity`
  - `T2_07_reject_variable_named_currentUser`
  - `T2_08_reject_variable_named_body`
  - `T2_09_reject_variable_named_id`
  - `T2_10_allow_variable_containing_reserved_word_as_substring`
- **Group 3: Self-Referential & Forward References (5 tests)**
  - `T2_11_reject_direct_self_referential_initialization`
  - `T2_12_reject_self_referential_in_update_predicate`
  - `T2_13_reject_self_referential_in_nested_field`
  - `T2_14_reject_forward_reference_to_variable_declared_later`
  - `T2_15_reject_mutual_circular_variable_reference`
- **Group 4: Invalid Property Access Resolution (5 tests)**
  - `T2_16_reject_nonexistent_property_access_on_bound_variable`
  - `T2_17_reject_typo_property_access_with_suggestion`
  - `T2_18_reject_accessing_field_of_different_model_type`
  - `T2_19_reject_invalid_relation_subfield_access`
  - `T2_20_reject_method_invocation_syntax_on_bound_variable`
- **Group 5: Syntax Errors & Precise Line/Column Reporting (5 tests)**
  - `T2_21_syntax_error_missing_variable_identifier`
  - `T2_22_syntax_error_missing_assignment_operator`
  - `T2_23_syntax_error_missing_target_model_name`
  - `T2_24_syntax_error_unclosed_action_block`
  - `T2_25_syntax_error_reports_exact_source_line_and_column`

### Tier 3: Cross-Feature Combinations (10 Tests)
- `T3_01_chained_create_two_entities_foreign_key_binding`
- `T3_02_chained_create_then_update_dependent_entity`
- `T3_03_chained_update_then_create_audit_entity`
- `T3_04_triple_chained_create_multi_level_relationship`
- `T3_05_variable_binding_following_precondition_guard`
- `T3_06_variable_binding_interleaved_with_field_mutations`
- `T3_07_variable_binding_populating_from_request_body`
- `T3_08_multiple_endpoints_on_same_model_independent_variables`
- `T3_09_variable_binding_in_patch_endpoint`
- `T3_10_variable_binding_with_enum_status_transitions`

### Tier 4: Real-World Enterprise Scenarios (5 Tests)
- `T4_01_ecommerce_order_fulfillment_checkout_workflow`: Customer checkout instantiating Order, generating Invoice with `order.id`, and recording Payment with `invoice.id` within an atomic `@Transactional` boundary.
- `T4_02_multitenant_saas_organization_onboarding`: Provisioning Tenant, creating initial Admin User bound to `tenant.id`, and creating Audit Log bound to `user.id`.
- `T4_03_banking_double_entry_funds_transfer`: Looking up Source and Target accounts, debiting/crediting, and recording Transaction Ledger with both account IDs.
- `T4_04_healthcare_clinical_intake_workflow`: Registering Patient, creating MedicalChart with `patient.id`, and creating InitialPrescription with `chart.id` and `patient.id`.
- `T4_05_logistics_waybill_and_shipment_dispatch`: Updating Order to SHIPPED, creating Shipment with `order.id`, and generating Waybill with `shipment.id`.

---

## 7. Expected Output Derivation & Verification Oracles

For every test case in the suite, expected outcomes are derived directly from the authoritative specifications:
1. **Lexer Tokens**: `TokenType.VAR` produced exclusively for the `"var"` keyword token outside string literals.
2. **Parser AST**: `ActionDef` / `VariableBindingActionDef` containing `variableName`, `actionType` ("create"/"update"), `targetModel`, and line/column numbers.
3. **Semantic Checks**: `AnalysisException` thrown with specific, non-empty error messages for duplicate variables, reserved words, self-references, and missing fields.
4. **Code Generation**:
   - Class declarations in `ServiceGenerator` must contain injected repositories for all models referenced in bindings.
   - Methods must include `@org.springframework.transaction.annotation.Transactional`.
   - Variable assignments must compile to `<Model> <var> = <model>Repository.save(<var>);`.
   - References `var.field` must translate to `var.getField()`.

---

## 8. Summary Scorecard & Metrics

| Tier | Focus Area | Test Count | Pass Criteria |
|:---:|---|:---:|---|
| **Tier 1** | Core Functional Coverage | 30 | 100% of happy-path tests pass |
| **Tier 2** | Boundary & Negative Checks | 25 | 100% of boundary & rejection tests pass |
| **Tier 3** | Combinatorial & Chaining | 10 | 100% of multi-entity combination tests pass |
| **Tier 4** | Real-World Workflows | 5 | 100% of complete enterprise workflows pass |
| **Total** | Full E2E Test Suite | **70** | Clean exit code 0 |
