# ISSUE-017: False Semantic Error on Enum Parameter Assignment (`status = new_status`)

- **Issue ID**: `ISSUE-017`
- **Date**: `2026-11-14`
- **Component**: `dev.zenith.analyzer.SemanticAnalyzer`, `dev.zenith.generator.LogicTranspiler`, `dev.zenith.parser.Parser`
- **Severity**: High (Blocks valid endpoint actions using enum parameters)
- **Status**: **RESOLVED & VERIFIED**

---

## 1. Error Summary

When an endpoint takes an enum parameter (via route path or `params: { ... }`) and assigns it to a model field in an `action` block:

```zen
put "/{id}/{new_status}/update_status" -> TaskResponse {
  params : {
    id : UUID
    new_status: TaskStatus
  }
  guard : status != new_status, "we cant use same status"
  action {
    status = new_status
  }
}
```

The analyzer threw a false semantic error at the assignment:

```
[main.zen:173] Semantic error: Invalid enum value 'new_status' for enum field 'status' of type 'TaskStatus' in endpoint 'PUT /{id}/{new_status}/update_status'. Available values: TODO, IN_PROGRESS, IN_REVIEW, COMPLETED
```

---

## 2. Root Cause

1. **`SemanticAnalyzer.java`**:
   - `actionVariables` was only populated with `body`, missing declared `endpoint.getParamTypes()` and path parameters `{...}`.
   - `validateEnumConstants` assumed any single identifier assigned to an enum field had to be a literal constant matching `Enum.getValues()`, instead of checking `localVars`, reserved variables, or model fields.
2. **`LogicTranspiler.java`**:
   - `qualifyEnumValue` checked `en.getName().equalsIgnoreCase(field.getType().getName()) || en.getValues().contains(value)`, causing variables assigned to enum fields (e.g. `new_status`) to transpile incorrectly as `TaskStatus.new_status`.
3. **`Parser.java`**:
   - Custom endpoint property parsing strictly required a colon after identifier tokens, failing or behaving inconsistently when `params` was written without a colon.

---

## 3. Resolution & Fix

1. **`SemanticAnalyzer.java`**:
   - Populated `actionVariables` with `endpoint.getParamTypes()` and all route path parameters.
   - Passed `localVars` into `validateEnumConstants`.
   - Bypassed the enum literal check if the assigned token matches a known in-scope variable, parameter, path parameter, model field, or reserved keyword.
2. **`LogicTranspiler.java`**:
   - Updated `qualifyEnumValue` to only prepend `EnumName.` if the value explicitly matches one of the declared enum constants in `en.getValues()`.
3. **`Parser.java`**:
   - Made the colon optional for `params` blocks (`params : { ... }` or `params { ... }`).

---

## 4. Verification

- Rebuilt CLI via `build-cli.bat`.
- Validated `zenith-exemples/taskflow/main.zen` containing `put "/{id}/{new_status}/update_status" -> TaskResponse` with `status = new_status`:
  ```
  >> Validating zenith-exemples/taskflow/main.zen...
  >> Schema is 100% valid!
     Found 4 models and 3 enums.
  ```
