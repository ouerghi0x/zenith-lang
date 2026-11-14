# ISSUE-011: Enum Method-Invocation Transpilation Bug in LogicTranspiler (`Enum.getValue()` vs `Enum.VALUE`)

**Issue ID**: `ISSUE-011`  
**Severity**: **P0 (Critical Runtime & Compilation Blocker)**  
**Status**: **RESOLVED & VERIFIED (Fixed in LogicTranspiler and ServiceGenerator)**  
**Discovered**: November 2026  
**Subsystems**: `dev.zenith.generator.LogicTranspiler`, `dev.zenith.generator.ServiceGenerator`  
**Reporter**: Antigravity Pair-Programming / Studio Live Build  

---

## 1. Problem Description & Symptoms

When running or building a Zenith application where an action statement or guard expression references an enum literal (e.g., `status = OrderStatus.PAID` or `status = OrderStatus.PENDING`), the generated Java code in `<Model>ServiceImpl.java` produces an invalid method invocation instead of an enum constant reference:

### Generated Invalid Java Code
```java
// In OrderServiceImpl.java:
entity.setStatus(OrderStatus.getPAID());   // ERROR: symbol not found 'method getPAID()'
// or
entity.setStatus(OrderStatus.getPENDING()); // ERROR: symbol not found 'method getPENDING()'
```

### Compiler / Maven Diagnostic
```
[ERROR] /.../OrderServiceImpl.java:[114,37] cannot find symbol
[ERROR]   symbol:   method getPENDING()
[ERROR]   location: class com.shopsphere.backend.model.OrderStatus
[INFO] BUILD FAILURE
```

---

## 2. Root Cause Analysis

In `LogicTranspiler.java`:

1. **Missing `file` Context in Action Assignment**:
   `transpileStatement` called `transpileValueExpression(right, model)` (without passing `file`). When `file == null`, `file.getEnums()` is unavailable to check declared enums.
2. **Greedy Chained Expression Dot-Conversion Pattern**:
   `Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)(?:\\.[a-zA-Z0-9_]+)+")` in `transpileValueExpression()` splits chained expressions like `OrderStatus.PAID` into `["OrderStatus", "PAID"]`.
   Because `file` was `null` or because the root name check didn't guard all cases, it converted the second segment into `.getPaid()` / `.getPAID()`, resulting in:
   `OrderStatus.getPAID()`!
3. **Guard Condition Expression**:
   `transpileCondition` also only accepted `ModelDef model` without `ZenithFile file`. In guard conditions, standalone enum references could be misidentified if matching field names or chained dot expressions.
4. **Unqualified Enum Literals**:
   In assignments like `status = PAID` (unqualified enum value), the value should be qualified to `OrderStatus.PAID` based on the field type or declared enums.

---

## 3. Solution Specification

1. **Pass `ZenithFile file`**:
   - Update `transpileStatement(String stmt, ModelDef model, ZenithFile file)` to pass `file` down into `transpileValueExpression(right, model, file)`.
   - Update `transpileCondition(String expr, ModelDef model, ZenithFile file)` and callers in `ServiceGenerator.java`.
2. **Guard Enum Constants in `transpileValueExpression`**:
   - Check if `root` is a declared enum name (case-insensitive or exact). If `root` matches any `EnumDef` in `file`, preserve the enum access as `EnumName.CONSTANT` without appending `.get<Constant>()`.
   - Even without `file`, check if `parts[parts.length - 1]` is an UPPERCASE enum identifier (e.g., `PENDING`, `PAID`, `SHIPPED`, `ACTIVE`, `CANCELLED`). If `parts[0]` begins with an uppercase letter and `parts[1]` is all-uppercase / uppercase-with-underscores, it is a Java static constant/enum and must NOT be converted to a getter method call.
3. **Qualify Unqualified Enum Constants**:
   - In field assignments `field = CONSTANT`, use `qualifyEnumValue(field, val, model, file)` to map `status = PAID` &rarr; `status = OrderStatus.PAID`.

---

## 4. Verification & Testing

- Add unit and end-to-end regression tests verifying that:
  - `status = OrderStatus.PAID` &rarr; `entity.setStatus(OrderStatus.PAID);`
  - `status = OrderStatus.PENDING` &rarr; `entity.setStatus(OrderStatus.PENDING);`
  - `guard: status == OrderStatus.PENDING` &rarr; `entity.getStatus() == OrderStatus.PENDING`
  - Unqualified `status = PAID` &rarr; `entity.setStatus(OrderStatus.PAID);`
- Verify that `mvn compile` in `schema.zen` and test suite runs with 0 errors.

