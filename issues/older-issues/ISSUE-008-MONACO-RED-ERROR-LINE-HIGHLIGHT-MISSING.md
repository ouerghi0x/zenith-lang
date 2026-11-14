# ISSUE-008: Red Error Line Highlighting & Squiggly Marker Not Rendered in Monaco Editor Due to Directory Name (`apiRestLanguage`) Returned as Error File

**Issue ID**: `ISSUE-008`  
**Severity**: **P1 (High / Studio Editor Diagnostics & UX)**  
**Status**: **FIXED & VERIFIED**  
**Discovered**: November 2026  
**Subsystems**: `dev.zenith.studio.ZenithStudioServer`, `dev.zenith.parser.SchemaResolver`, `src/main/resources/studio/studio.js`, `src/main/resources/studio/studio.css`

---

## 1. Problem Description & Symptom

When a syntax or semantic error occurs in Zenith Studio (e.g., an unsupported annotation `@uniques` on line 74 of `schema.zen`):
1. The bottom Diagnostic drawer appeared and showed:
   `apiRestLanguage Line 74, Col 1: [apiRestLanguage:74] Semantic error: ...`
2. The top header bar showed:
   `Error at line 74 in apiRestLanguage`
3. **However, line 74 inside the Monaco code editor had NO red line highlight and NO red squiggly underline!**

---

## 2. Root Cause Analysis (RCA)

1. **Backend (`ZenithStudioServer.java` & `SchemaResolver.java`)**:
   - In `ZenithStudioServer.CompileHandler`, when compiling the root schema file, it called:
     ```java
     ast = resolver.resolveSource(source, baseDir);
     ```
     passing `baseDir` (the parent directory `C:\Users\...\apiRestLanguage`) instead of `activeFilePath` (`C:\Users\...\apiRestLanguage\schema.zen`).
   - Inside `SchemaResolver.resolveSource(String rootSource, Path basePath)`, it derived the source file name via:
     ```java
     String rootFileName = basePath != null ? basePath.getFileName().toString() : "main.zen";
     ```
   - Because `basePath` was the directory `apiRestLanguage`, `basePath.getFileName().toString()` evaluated to `"apiRestLanguage"` instead of `"schema.zen"`!
2. **Frontend (`studio.js`)**:
   - In `studio.js`, Monaco markers were only applied if `data.file === window.currentActiveFile`:
     ```javascript
     if (!data.file || data.file === window.currentActiveFile) {
       monaco.editor.setModelMarkers(editor.getModel(), 'zenith', [...]);
     }
     ```
   - Because `data.file` was `"apiRestLanguage"` while `window.currentActiveFile` was `"schema.zen"`, the condition evaluated to `false` and **cleared all Monaco markers** (`setModelMarkers(..., [])`) instead of highlighting line 74!

---

## 3. Resolution

1. **Pass `activeFilePath` in `ZenithStudioServer.java` & `SchemaResolver.java`**:
   - Updated `ZenithStudioServer` to pass `activeFilePath` (`.../schema.zen`) to `resolver.resolveSource(source, activeFilePath)`.
   - Updated `SchemaResolver.resolveSource` so `rootFileName` is always the `.zen` filename (`schema.zen`) while `currentDir` resolves to its parent directory.
2. **Full-Line Red Highlight & Gutter Decoration in `studio.js` & `studio.css`**:
   - Added `editor.deltaDecorations` with `isWholeLine: true`, applying `.zenith-error-line-highlight` (`background-color: rgba(239, 68, 68, 0.22); border-left: 3px solid #ef4444;`) and `.zenith-error-line-gutter` directly to the offending line in Monaco Editor, in addition to full-line red squiggly markers (`monaco.MarkerSeverity.Error`).
   - Automatically clears the red line highlight as soon as the user types or the schema compiles cleanly.

