# ISSUE-006: Model-Level `@index` / `@uniqueIndex` Placed Directly After Fields (Above `relations`) Greedily Consumed by `parseField()`

**Issue ID**: `ISSUE-006`  
**Severity**: **P1 (High / Parser Grammar Bug)**  
**Status**: **FIXED & VERIFIED**  
**Discovered**: November 2026  
**Subsystems**: `dev.zenith.parser.Parser`, `dev.zenith.ast.ModelDef`

---

## 1. Problem Description & Symptom

When declaring model-level composite indexes (`@index([...])` or `@uniqueIndex([...])`) inside a `model` block:
- **Placing them AFTER `relations { ... }`**: Worked as expected, emitting all `@Index` annotations on `@Table`.
- **Placing them BEFORE `relations { ... }` (directly underneath the model's fields)**: Failed with a compilation/semantic error at the model declaration line (`Error at line 72 in apiRestLanguage`).

### Reproduction Schema
```zenith
model Order {
    id: UUID @id @auto
    orderNumber: String(length = 20) @unique
    totalAmount: Decimal(precision = 12, scale = 2) @positive
    status: OrderStatus = PENDING
    createdAt: DateTime @autoNow

    // Placed directly after fields and BEFORE relations { ... }
    @index([customer, status])
    @index([status, createdAt], name = "idx_orders_status_timeline")
    @uniqueIndex([orderNumber, customer])

    relations {
        customer: Customer @relation(manyToOne)
    }
}
```

---

## 2. Root Cause Analysis (RCA)

In `Parser.parseField()`:
```java
while (peek().getType() == TokenType.AT || peek().getType() == TokenType.EQUAL) {
    if (match(TokenType.EQUAL)) {
        defaultValue = parseLiteralOrIdentifier();
    } else if (peek().getType() == TokenType.AT) {
        annotations.add(parseAnnotation());
    }
}
```
Because Zenith lexer/parser is whitespace- and newline-agnostic inside `parseField()`, the `while (peek().getType() == TokenType.AT ...)` loop for the last field (`createdAt: DateTime @autoNow`) **continued across newlines** and greedily consumed all three `@index(...)` / `@uniqueIndex(...)` declarations as **inline field annotations** on `createdAt` instead of returning control to `parseModel()`!

Consequently:
1. `ModelDef.getEffectiveIndexes()` treated those annotations as 3 inline single-column indexes on `createdAt` (`@index` on field `createdAt`).
2. `SemanticAnalyzer.validateModelIndexes()` threw an `AnalysisException` for redundant duplicate indexes on `createdAt`:
   `Redundant duplicate index in model 'Order': @index on field 'createdAt' covers the same columns [createdAt] as an earlier index declaration.`

When `@index([...])` was placed *after* `relations { ... }`, `parseRelationsBlock()` terminated with `}`, returning directly to `parseModel()`'s main loop where `cur.getType() == TokenType.AT` properly dispatched to `parseModelIndexDeclaration()`.

---

## 3. Resolution

Updated `Parser.parseField()` with `isModelLevelIndexAhead(int fieldLine)` lookahead so that `@index([...])`, `@uniqueIndex([...])`, or `@index(fields = [...])` appearing on a subsequent line terminates field annotation parsing and returns to `parseModel()`:

```java
private boolean isModelLevelIndexAhead(int fieldLine) {
    Token atTok = peek();
    if (atTok.getType() != TokenType.AT || atTok.getLine() <= fieldLine) {
        return false;
    }
    Token nameTok = peekNext();
    String annName = nameTok.getValue();
    if (!"index".equalsIgnoreCase(annName) && !"uniqueIndex".equalsIgnoreCase(annName)) {
        return false;
    }
    if (pos + 2 < tokens.size() && tokens.get(pos + 2).getType() == TokenType.LPAREN) {
        if (pos + 3 < tokens.size()) {
            Token firstArg = tokens.get(pos + 3);
            if (firstArg.getType() == TokenType.LBRACKET
                    || (firstArg.getType() == TokenType.IDENTIFIER && "fields".equalsIgnoreCase(firstArg.getValue()))) {
                return true;
            }
        }
    }
    return false;
}
```

---

## 4. Regression Test

Added `T1_10_parse_model_indexes_placed_before_relations_block` in `Feature2DatabaseIndexesTest.java`, verifying that placing `@index([customer, status])`, `@index([status, createdAt], name = "...")`, and `@uniqueIndex([orderNumber, customer])` above `relations { ... }` parses all 3 model-level composite indexes and emits identical JPA `@Index` and Flyway `CREATE INDEX` statements.

