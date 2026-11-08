# Test Suite Ready: Zenith Feature #1 (Action Variable Binding)

**Publication Date**: 2026-11-08T21:00:00Z  
**Status**: `TESTS_ACTIVE` — Complete 4-Tier Test Suite Ready for Verification  
**Author**: E2E Testing Track Test Writer  

---

## 1. Quick Start / Test Execution Command

To execute the full opaque-box E2E test suite:

```cmd
:: Windows CMD (Recommended)
run-tests.bat
```

```powershell
# Windows PowerShell
.\run-tests.ps1
```

### Targeted Tier Execution

```cmd
run-tests.bat --tier 1    # Tier 1: Feature Coverage (30 tests)
run-tests.bat --tier 2    # Tier 2: Boundary & Negative Cases (25 tests)
run-tests.bat --tier 3    # Tier 3: Cross-Feature Combinations (10 tests)
run-tests.bat --tier 4    # Tier 4: Real-World Application Scenarios (5 tests)
```

---

## 2. Test Suite Inventory & Coverage Summary

The test suite contains **70 automated test cases** across all four tiers defined in `PROJECT.md` and `private/specs/01-action-variable-binding.md`:

| Tier | Category | Test Count | Features Covered | Status |
|:---:|---|:---:|---|:---:|
| **Tier 1** | **Feature Coverage** | **30** | F1, F2, F3, F4, F5, F6, F7, F11, F12, F13, F14, F15, F16, F17, F18 | Ready |
| **Tier 2** | **Boundary & Negative Cases** | **25** | F8, F9, F10, Line/Col Syntax Error Reporting | Ready |
| **Tier 3** | **Cross-Feature Combinations** | **10** | F22 (Chained bindings, Mixed CRUD, Guards, Payloads) | Ready |
| **Tier 4** | **Real-World Scenarios** | **5** | F23 (E-Commerce, Multi-Tenant, Banking, Healthcare, Logistics) | Ready |
| **Total** | **Full Opaque-Box E2E Suite** | **70** | **All 25 Features Mapped** | **ACTIVE** |

---

## 3. Tier Breakdown & Test Cases

### Tier 1: Feature Coverage (`Tier1FeatureCoverageTest.java` — 30 Tests)
- **Group 1: Lexer & Token Recognition (5 tests)**
  - `T1_01_lexer_recognizes_var_keyword`: Recognizes `"var"` keyword token.
  - `T1_02_lexer_var_token_case_sensitivity`: Case sensitivity (`Var` vs `var`).
  - `T1_03_lexer_allows_var_as_field_name_backward_compatibility`: Preserves legacy schemas with fields named `var`.
  - `T1_04_lexer_var_inside_string_literals_not_tokenized_as_keyword`: Literal string content protection.
  - `T1_05_lexer_multiple_consecutive_var_tokens`: Multiple consecutive variable keywords.
- **Group 2: Grammar & AST Instantiation (5 tests)**
  - `T1_06_parser_creates_variable_binding_create_action`: `var x = create Model {}`.
  - `T1_07_parser_creates_variable_binding_update_action`: `var x = update Model {}`.
  - `T1_08_parser_variable_binding_with_field_assignments`: Field assignments in RHS.
  - `T1_09_parser_preserves_action_statement_order`: Sequence order in AST.
  - `T1_10_parser_variable_binding_metadata_line_column`: AST source position metadata.
- **Group 3: Scoping & Type Inference (5 tests)**
  - `T1_11_semantics_infers_model_type_from_create_expression`: RHS type inference for `create`.
  - `T1_12_semantics_infers_model_type_from_update_expression`: RHS type inference for `update`.
  - `T1_13_semantics_registers_variable_in_action_symbol_table`: Action block symbol table registration.
  - `T1_14_semantics_variable_visible_to_subsequent_statements`: Visibility in downstream statements.
  - `T1_15_semantics_multiple_distinct_variables_different_types`: Distinct model types in same scope.
- **Group 4: Property Resolution & DB-Generated Fields (5 tests)**
  - `T1_16_semantics_resolves_declared_model_field`: Model field access on bound variable.
  - `T1_17_semantics_resolves_db_generated_id_field`: DB-generated `id` resolution.
  - `T1_18_semantics_resolves_db_generated_created_at_field`: DB-generated `createdAt` resolution.
  - `T1_19_semantics_resolves_db_generated_updated_at_field`: DB-generated `updatedAt` resolution.
  - `T1_20_semantics_resolves_relation_navigation_field`: Relation navigation on bound variable.
- **Group 5: Transpilation & Repository Auto-Injection (5 tests)**
  - `T1_21_codegen_transpiles_var_create_to_java_save_assignment`: Transpiles to `<Model> <var> = repo.save(...)`.
  - `T1_22_codegen_transpiles_var_update_to_java_find_and_save`: Transpiles to `findById(...)` and `repo.save(...)`.
  - `T1_23_codegen_replaces_bound_property_with_getter`: Dot notation to Java getter call (`var.getProp()`).
  - `T1_24_codegen_auto_injects_extra_repositories_into_service`: Injects required Spring Data repositories.
  - `T1_25_codegen_transpiles_nested_getter_chain`: Nested property getter chain (`var.getRel().getProp()`).
- **Group 6: Transactional Boundary & Method Semantics (5 tests)**
  - `T1_26_codegen_annotates_action_with_transactional`: Generates Spring `@Transactional` on action method.
  - `T1_27_codegen_ensures_transactional_import_present`: Imports `org.springframework.transaction.annotation.Transactional`.
  - `T1_28_codegen_preserves_execution_sequence_in_java_output`: Statement order preserved in Java service method.
  - `T1_29_codegen_handles_void_return_custom_action`: Void return custom action.
  - `T1_30_codegen_handles_dto_return_custom_action`: DTO return custom action.

### Tier 2: Boundary & Corner Cases (`Tier2BoundaryNegativeTest.java` — 25 Tests)
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
- **Group 4: Non-Existent / Invalid Property Access (5 tests)**
  - `T2_16_reject_nonexistent_property_access_on_bound_variable`
  - `T2_17_reject_typo_property_access_with_suggestion`
  - `T2_18_reject_accessing_field_of_different_model_type`
  - `T2_19_reject_invalid_relation_subfield_access`
  - `T2_20_reject_method_invocation_syntax_on_bound_variable`
- **Group 5: Syntax Errors & Position Reporting (5 tests)**
  - `T2_21_syntax_error_missing_variable_identifier`
  - `T2_22_syntax_error_missing_assignment_operator`
  - `T2_23_syntax_error_missing_target_model_name`
  - `T2_24_syntax_error_unclosed_action_block`
  - `T2_25_syntax_error_reports_exact_source_line_and_column`

### Tier 3: Cross-Feature Combinations (`Tier3CombinationsTest.java` — 10 Tests)
- `T3_01_chained_create_two_entities_foreign_key_binding`: Chained create propagating foreign key ID.
- `T3_02_chained_create_then_update_dependent_entity`: Chained create followed by dependent update.
- `T3_03_chained_update_then_create_audit_entity`: Chained update followed by audit creation.
- `T3_04_triple_chained_create_multi_level_relationship`: Triple chained entity creation.
- `T3_05_variable_binding_following_precondition_guard`: Guard followed by variable binding.
- `T3_06_variable_binding_interleaved_with_field_mutations`: Interleaved raw assignments and variable bindings.
- `T3_07_variable_binding_populating_from_request_body`: Binding fields from request body payload.
- `T3_08_multiple_endpoints_on_same_model_independent_variables`: Scoping isolation across endpoints.
- `T3_09_variable_binding_in_patch_endpoint`: Variable binding in PATCH endpoint.
- `T3_10_variable_binding_with_enum_status_transitions`: Variable binding with Enum assignments.

### Tier 4: Real-World Scenarios (`Tier4RealWorldScenariosTest.java` — 5 Tests)
- `T4_01_ecommerce_order_fulfillment_checkout_workflow`: Complete checkout (Order -> Invoice -> Payment -> Notification) with atomic `@Transactional` boundary.
- `T4_02_multitenant_saas_organization_onboarding`: Tenant creation, Admin user provisioning, and audit log generation.
- `T4_03_banking_double_entry_funds_transfer`: Source and target account lookups, adjustments, and ledger record creation.
- `T4_04_healthcare_clinical_intake_workflow`: Patient intake, clinical chart creation, and prescription issuance.
- `T4_05_logistics_waybill_and_shipment_dispatch`: Order dispatch, shipment creation, and waybill generation.

---

## 4. Key Verification Artifacts

1. **`TEST_INFRA.md`**: Complete test architecture, test tier definitions, test methodologies (Category-Partition, Boundary Value Analysis, Pairwise, Real-World Workloads).
2. **`run-tests.bat` / `run-tests.ps1`**: Standalone runner compiling CLI + test classes and executing test suite.
3. **`src/test/java/dev/zenith/test/*`**:
   - `TestHarness.java`
   - `Tier1FeatureCoverageTest.java`
   - `Tier2BoundaryNegativeTest.java`
   - `Tier3CombinationsTest.java`
   - `Tier4RealWorldScenariosTest.java`
   - `E2ETestSuiteRunner.java`

---

## 5. Milestone Progression Guidance for Implementation Agents

The test suite is compiled and operational. As implementing agents complete each milestone, they can run `run-tests.bat` to track progress:
- **M1 (Lexer)**: Fixes token recognition for `var` keyword.
- **M2 (Parser & AST)**: Fixes grammar parsing for `var <id> = create / update` and creates `VariableBindingActionDef`.
- **M3 (Semantic Analyzer & Symbol Table)**: Implements action-level scoping, detects duplicate declarations, rejects reserved words (`entity`, `currentUser`, `body`, `id`), rejects self-referential initialization, and resolves field accesses against target models.
- **M4 (Service Generator & Transpiler)**: Auto-injects extra repositories into service classes, transpiles variable bindings to Java local variable assignments, replaces dot-notation properties with getters, and annotates methods with Spring `@Transactional`.
- **M5 (Hardening & 100% Pass)**: All 70 test cases achieve 100% pass rate.
