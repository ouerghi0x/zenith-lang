# Project: Zenith Feature #1 — Action Variable Binding (`var x = create / update ...`)

## Architecture

The Zenith Compiler converts `.zen` domain-specific specifications into production Spring Boot / JPA applications. Feature #1 adds local variable bindings within action blocks, allowing developers to capture persisted entity instances (`var x = create Order { ... }` or `var x = update Order { ... }`) and reference their properties (e.g., `x.id`, `x.totalAmount`) in downstream operations within a unified `@Transactional` boundary.

### Compiler Pipeline & Data Flow
1. **Lexical Analysis (`Lexer.java`, `TokenType.java`)**:
   - Tokenizes `"var"` as `TokenType.VAR`.
   - Passes token stream to Parser.
2. **Parser & AST (`Parser.java`, `dev.zenith.ast.*`)**:
   - Parses action blocks preserving lexical statement order.
   - Detects `var <identifier> = create / update ...` and instantiates `VariableBindingActionDef`.
   - Reports line/col syntax errors for malformed variable declarations.
   - Wraps other action statements in `RawActionDef` (or `ActionDef` hierarchy), maintaining backward compatibility with `CustomEndpointDef.getActionStatements()`.
3. **Semantic Analysis (`SemanticAnalyzer.java`, `SymbolTable.java`)**:
   - Allocates a scoped `SymbolTable` for each action block.
   - Rejects duplicate variable declarations within the block.
   - Protects reserved identifiers (`entity`, `currentUser`, `body`, `id`).
   - Rejects illegal forward/self-referential initialization (RHS referencing the bound variable).
   - Infers model types and validates downstream property accesses (`x.property`) against the bound `ModelDef` and DB-generated attributes (`id`, `createdAt`, `updatedAt`).
4. **Code Generation & Transpilation (`ServiceGenerator.java`, `LogicTranspiler.java`)**:
   - Scans action statements for bound models and auto-injects required Spring Data repositories (`extraRepos`).
   - Ensures custom action methods are wrapped in `@Transactional`.
   - Transpiles `var x = create Model { ... }` into Java local variable declarations and `repository.save(...)` assignments.
   - Transpiles `var x = update Model { ... }` into entity lookup and `repository.save(...)` assignments.
   - Rewrites downstream property accesses (`x.prop`) into Java getters (`x.getProp()`).
   - Preserves strict lexical execution order without splitting into primary/secondary buckets.
5. **Build & Test Verification (`build-cli.bat`, Maven 3.9.6)**:
   - Verifies compiler build with `build-cli.bat`.
   - Executes compiler unit and integration tests.
   - Validates generated Spring Boot code using `tools\apache-maven-3.9.6\bin\mvn.cmd test-compile`.

---

## Feature Inventory

| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | `var` Keyword Token | Recognize `"var"` as `TokenType.VAR` in lexer and keyword map | M1 | Spec §3, Survey |
| 2 | Action Variable Binding Grammar (`create`) | Parse `var <id> = create <Model> { ... }` in action blocks | M2 | Spec §2, §5 |
| 3 | Action Variable Binding Grammar (`update`) | Parse `var <id> = update <Model> { ... }` in action blocks | M2 | Spec §2, §5 |
| 4 | `VariableBindingActionDef` AST Node | Dedicated AST node encapsulating identifier, action type, target model, expression, line/col | M2 | Spec §4 |
| 5 | Action Statement Execution Order Preservation | Maintain strict textual order of action statements in AST and generators | M2 | Spec §3 Step 2 |
| 6 | Action-Level Symbol Table Scoping | Implement `SymbolTable.java` managing block-scoped symbols with parent/child links | M3 | Spec §3 Step 4 |
| 7 | Inferred Model Type Registration | Infer target model type from RHS and associate symbol with `ModelDef` in `SymbolTable` | M3 | Spec §5 |
| 8 | Duplicate Variable Declaration Guard | Prevent declaring multiple variables with the same name in the same action scope | M3 | Spec §7, AC |
| 9 | Reserved Identifier Protection | Prevent shadowing contextual keywords `entity`, `currentUser`, `body`, `id` | M3 | Spec §7, AC |
| 10 | Self-Referential Initialization Guard | Prevent RHS initialization from referencing the variable being declared | M3 | Spec §7, AC |
| 11 | Bound Variable Property Resolution | Validate that property accesses on bound variable (`x.prop`) match target model fields | M3 | Spec §2, §7, AC |
| 12 | Database Generated Attribute Access | Validate access to DB-generated attributes (`id`, `createdAt`, `updatedAt`) on bound variables | M3 | Spec §2, Survey |
| 13 | Extra Repository Auto-Injection | Scan bound actions and inject required Spring Data repositories into service constructor | M4 | Spec §5, Survey |
| 14 | Local Variable Transpilation (`create`) | Transpile `var x = create Model` into Java declaration, setters, and `repository.save(...)` | M4 | Spec §3 Step 5, AC |
| 15 | Local Variable Transpilation (`update`) | Transpile `var x = update Model` into entity lookup, setters, and `repository.save(...)` | M4 | Spec §3 Step 5, AC |
| 16 | Bound Variable Getter Replacement | Transpile dot-notation on bound variables to Java getter calls (`x.field` -> `x.getField()`) | M4 | Spec §3 Step 5, AC |
| 17 | Nested Property Getter Chaining | Transpile nested navigation on bound variables (`x.rel.prop` -> `x.getRel().getProp()`) | M4 | Spec §2, Survey |
| 18 | Spring `@Transactional` Boundary | Annotate multi-entity action methods with Spring `@Transactional` for rollback safety | M4 | Spec §6, §7, AC |
| 19 | E2E Test Infrastructure & Harness | Build test runner and test harness for compiling and verifying Zenith schemas and Java code | E2E | Spec §8, Survey |
| 20 | Tier 1 Feature Coverage Tests | Happy-path coverage of isolated variable binding capabilities (>=5 tests per feature) | E2E | Spec §8 |
| 21 | Tier 2 Boundary & Negative Tests | Rejection tests for duplicates, reserved words, self-references, syntax errors (>=5 per feature) | E2E | Spec §8 |
| 22 | Tier 3 Cross-Feature & Chaining Tests | Tests for multi-variable chaining (`var a = create A; var b = create B { aId = a.id }`) | E2E | Spec §8 |
| 23 | Tier 4 Real-World Application Scenarios | Comprehensive checkout/order/invoice workflows with transactional guarantees | E2E | Spec §8 |
| 24 | Compiler Build & Packaging Verification | Clean compilation and packaging via `build-cli.bat` | M5 | AC |
| 25 | Generated Code Maven Compilation | Validation that generated Spring Boot projects compile cleanly via Maven 3.9.6 | M5 | AC |

---

## Milestones

| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| E2E | E2E Testing Track | Design test infra (`TEST_INFRA.md`), test cases Tiers 1-4, publish `TEST_READY.md` | none | DONE |
| M1 | Lexer & Token Recognition | Add `TokenType.VAR`, register `"var"` keyword, handle token fallback in Parser | none | DONE |
| M2 | Parser & AST Support | Add `ActionDef`, `VariableBindingActionDef`, `RawActionDef`, update `Parser.java`, preserve sequence | M1 | IN_PROGRESS |
| M3 | Semantic Analysis & Scoping | Implement `SymbolTable.java`, action scoping, duplicate/reserved/self-ref guards, property resolution | M2 | PLANNED |
| M4 | Service Generator & Transpilation | `extraRepos` scan, sequential codegen, `@Transactional`, getter replacement, save assignment | M3 | PLANNED |
| M5 | Final Verification & Hardening | Phase 1: 100% pass on E2E test suite (Tiers 1-4); Phase 2: Adversarial coverage hardening (Tier 5) | M4, E2E | PLANNED |

---

## Interface Contracts

### 1. Lexer ↔ Parser
- `TokenType.VAR`: Represents keyword `"var"`.
- `Lexer.KEYWORDS`: Contains mapping `"var" -> TokenType.VAR`.
- `Parser.consumeFieldName()` and `consumeKeyName()`: Must accept `TokenType.VAR` as a valid identifier to ensure backward compatibility for legacy schemas having fields or keys named `var`.

### 2. Parser ↔ AST
- `dev.zenith.ast.ActionDef`: Abstract base class representing an action statement:
  - `String getSourceFile()`, `int getSourceLine()`, `int getSourceColumn()`, `String getRawStatement()`.
- `dev.zenith.ast.VariableBindingActionDef extends ActionDef`:
  - `String getVariableName()`: Variable identifier (e.g., `"newOrder"`).
  - `String getActionType()`: `"create"` or `"update"`.
  - `String getTargetModel()`: Target model name (e.g., `"Order"`).
  - `String getInitExpression()`: Full RHS action statement (e.g., `"create Order { total = 100 }"`).
  - `String getInferredType()`: Model type name.
  - `String getRawStatement()`: `"var " + variableName + " = " + initExpression`.
- `dev.zenith.ast.RawActionDef extends ActionDef`:
  - Encapsulates legacy raw action statements (`statement`, `file`, `line`, `col`).
- `CustomEndpointDef` and `JobDef`:
  - `List<ActionDef> getActions()`: Primary typed action list.
  - `List<String> getActionStatements()`: Backward-compatible getter returning raw statement strings.

### 3. AST / Parser ↔ Semantic Analyzer
- `dev.zenith.analyzer.SymbolTable`:
  - Scoped data structure storing `Symbol(String name, ModelDef modelDef, String file, int line, int col)`.
  - Methods: `void define(String name, ModelDef model, String file, int line, int col)`, `Symbol lookup(String name)`, `boolean isDeclaredLocally(String name)`, `boolean isReserved(String name)`.
  - Reserved names: `{"entity", "currentUser", "body", "id"}`.
- Semantic Validation Rules:
  - If `isReserved(name)` -> throw `AnalysisException("Cannot use reserved identifier '" + name + "' as action variable", file, line, col)`.
  - If `isDeclaredLocally(name)` -> throw `AnalysisException("Duplicate variable declaration '" + name + "' in action block", file, line, col)`.
  - If RHS references `name` -> throw `AnalysisException("Illegal forward reference or self-referential initialization of variable '" + name + "'", file, line, col)`.
  - If `x.property` is accessed where `x` is bound to `ModelDef`: verify `property` exists on `ModelDef` or is one of `{"id", "createdAt", "updatedAt"}`. Otherwise throw `AnalysisException("Model '" + model.getName() + "' has no field or relation '" + property + "' on variable '" + name + "'", file, line, col)`.

### 4. Semantic Analyzer ↔ Service Generator & Logic Transpiler
- `ServiceGenerator.java`:
  - `extraRepos` scan: Must inspect `var <id> = create <Model>` and `var <id> = update <Model>`, adding `<Model>` to `extraRepos`.
  - Statement generation: Must execute action statements in textual order.
  - `@Transactional`: Custom action methods performing multi-entity writes must be annotated with `@Transactional`.
- `LogicTranspiler.java`:
  - `transpileStatement(String stmt, ...)` / `transpileAction(ActionDef action, ...)`:
    - `var <id> = create <Model> { ... }`: Transpiles to `<Model> <id> = new <Model>(); ... <id> = <model>Repository.save(<id>);`.
    - `var <id> = update <Model> { ... }`: Transpiles to `<Model> <id> = <model>Repository.findById(...).orElseThrow(...); ... <id> = <model>Repository.save(<id>);`.
  - `transpileValueExpression(...)`:
    - Given active bound variable symbols, rewrites `<varName>.<field>` to `<varName>.get<Field>()` and `<varName>.<rel>.<field>` to `<varName>.get<Rel>().get<Field>()`.

---

## Code Layout & File Boundaries

| File / Path | Component | Owner Milestone | Description |
|-------------|-----------|-----------------|-------------|
| `src/main/java/dev/zenith/lexer/TokenType.java` | Lexer | M1 | Enum token definitions (`VAR`) |
| `src/main/java/dev/zenith/lexer/Lexer.java` | Lexer | M1 | Tokenizer implementation & keyword map |
| `src/main/java/dev/zenith/ast/ActionDef.java` | AST | M2 | Abstract base class for action statements |
| `src/main/java/dev/zenith/ast/VariableBindingActionDef.java` | AST | M2 | AST node for `var x = create / update ...` |
| `src/main/java/dev/zenith/ast/RawActionDef.java` | AST | M2 | AST node for standard action statements |
| `src/main/java/dev/zenith/ast/CustomEndpointDef.java` | AST | M2 | Endpoint AST with typed actions |
| `src/main/java/dev/zenith/ast/JobDef.java` | AST | M2 | Job AST with typed actions |
| `src/main/java/dev/zenith/parser/Parser.java` | Parser | M1 (token acceptance), M2 (grammar) | Action parsing & syntax error reporting |
| `src/main/java/dev/zenith/analyzer/SymbolTable.java` | Semantics | M3 | Action block symbol table and scoping |
| `src/main/java/dev/zenith/analyzer/SemanticAnalyzer.java` | Semantics | M3 | Semantic checks & property validation |
| `src/main/java/dev/zenith/generator/ServiceGenerator.java` | Generator | M4 | Extra repos, sequencing, `@Transactional` |
| `src/main/java/dev/zenith/generator/LogicTranspiler.java` | Transpiler | M4 | Statement & expression transpilation |
| `src/test/java/dev/zenith/test/...` | Tests | E2E, M5 | Test suite & verification harness |
| `TEST_INFRA.md` | Testing Track | E2E | Test infrastructure specification |
| `TEST_READY.md` | Testing Track | E2E | Signal that E2E test suite is ready |

