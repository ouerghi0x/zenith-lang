# ISSUE-007: Unrecognized or Misspelled Field Annotations (`@uniquesz`, `@autoNows`) Silently Ignored by Compiler

**Issue ID**: `ISSUE-007`  
**Severity**: **P1 (High / Compiler Diagnostics & Silent Data Integrity Bug)**  
**Status**: **FIXED & VERIFIED**  
**Discovered**: November 2026  
**Subsystems**: `dev.zenith.analyzer.SemanticAnalyzer`, `dev.zenith.ast.AnnotationDef`, `dev.zenith.parser.Parser`

---

## 1. Problem Description & Symptom

When a developer misspells a field decorator or writes an unsupported annotation in a `.zen` schema (for example, `@uniquesz` instead of `@unique`, or `@autoNows` instead of `@autoNow`), the Zenith compiler previously accepted the schema as **"Valid Schema"** without reporting any syntax or semantic error.

### Impact
- Misspelled `@uniquesz` silently dropped the SQL `UNIQUE` constraint and `findBy<Field>` repository query.
- Misspelled `@autoNows` silently dropped the `@PrePersist` creation timestamp hook and caused DTO validation to demand `createdAt` in `Create<Model>Request` payloads.
- Misspelled `@passwords` or `@emails` silently bypassed password hashing or email validation.

---

## 2. Root Cause Analysis (RCA)

1. `Parser.parseAnnotation()` accepted any `@<identifier>` token and attached an `AnnotationDef` to `FieldDef.annotations`.
2. Downstream generators (`EntityGenerator`, `DtoGenerator`, `FlywayGenerator`, `TypeHelper`) checked for specific known annotations via `field.hasAnnotation("unique")`, `field.hasAnnotation("autoNow")`, etc.
3. `SemanticAnalyzer` validated specific rules *if* a known annotation was present (e.g. checking `DateTime` type when `@autoNow` is present), but **never validated that every annotation attached to a field belongs to the set of supported Zenith annotations**.

---

## 3. Resolution

1. **Source Line Tracking on `AnnotationDef`**:
   - Added `sourceLine` to `AnnotationDef` and populated it in `Parser.parseAnnotation()` so that diagnostic errors point to the exact line of the invalid `@annotation` in Zenith Studio.
2. **Strict Whitelist & Typo Suggestion in `SemanticAnalyzer`**:
   - Added `SUPPORTED_FIELD_ANNOTATIONS` registry in `SemanticAnalyzer`.
   - Every annotation on every model field is checked against `SUPPORTED_FIELD_ANNOTATIONS`.
   - If an unsupported or misspelled annotation is found, `SemanticAnalyzer` throws an `AnalysisException` at the exact annotation line with a Levenshtein closest-match suggestion:
     - `Unsupported field annotation '@uniquesz' on field 'orderNumber' in model 'Order'. Did you mean '@unique'?`
     - `Unsupported field annotation '@autoNows' on field 'createdAt' in model 'Order'. Did you mean '@autoNow'?`
3. **Relation Block Annotation Validation**:
   - Updated `Parser.parseRelationsBlock()` to also reject invalid relation annotations with a clear `ParseException`.

---

## 4. Verification

Added `T2_12_reject_unsupported_field_annotation_with_suggestion` in `Feature2DatabaseIndexesTest.java`, verifying that both `@uniquesz` and `@autoNows` are caught immediately with `@unique` and `@autoNow` suggestions.

