# ISSUE-018: Meme API Generation Fails and Zenith Highlights the Wrong Line

- **Issue ID**: `ISSUE-018`
- **Date**: `2026-11-14`
- **Component**: Zenith Studio compiler diagnostics, endpoint `crud` code generation, generated Spring Boot controller
- **Severity**: High (Blocks API generation for the affected schema and misdirects debugging)
- **Status**: **PARTIALLY RESOLVED — Source highlighting fixed; duplicate-method report not reproducible from current source**

---

## 1. Error Summary

The attached screenshot records a failed Maven build with a duplicate controller method and a schema highlight on the `crud {` line:

```text
TaskController.java:[102,41] method updateStatus(java.util.UUID, com.zenith.taskflow.model.TaskStatus)
is already defined in class com.zenith.taskflow.controller.TaskController
```

The build ended with `BUILD FAILURE` (Maven compiler plugin, exit code 1). The screenshot’s Task schema has an `endpoints { crud { ... } }` block with pagination, authentication, delete role, and public-list settings, followed by custom endpoints. However, the current checked-in TaskFlow schema generates only one `updateStatus(UUID, TaskStatus)` method and one `updatePriority(UUID, TaskPriority)` method; the duplicate-method failure cannot currently be reproduced from the repository state.

At the same time, Zenith Studio reports the failure at **line 133, column 9**, highlighting the opening `crud {` line. That location does not identify the duplicate method and is misleading: the reported Java error is in generated `TaskController.java` at line 102.

The supplied `image.png` and `image-1.png` show the same failing build and highlight. The original issue note mentions writing a meme API without errors, but no meme-specific schema or reproduction was included.

---

## 2. Expected Behavior

- The meme API schema should generate and compile successfully when its endpoint declarations are valid.
- If code generation produces duplicate Java methods, the diagnostic should identify the conflicting generated method and map the error to the schema declaration(s) responsible. It should not attribute a generated Java compilation error to `crud {` unless that line is actually the source of the conflict.

---

## 3. Actual Behavior

- Generated Spring Boot compilation fails because `TaskController` declares `updateStatus(UUID, TaskStatus)` more than once.
- Zenith displays `Line: 133:9` and highlights the `crud {` declaration, which does not match the generated Java compiler location or explain the duplicate.
- The UI labels the event an unexpected internal compilation issue, leaving the user without a useful schema-level diagnosis.

---

## 4. Reproduction Evidence

1. Open the schema shown in the attached screenshots in Zenith Studio.
2. Run API generation/build.
3. Observe the Maven compiler failure for duplicate `TaskController.updateStatus(UUID, TaskStatus)` at generated Java line 102.
4. Observe Zenith highlight `crud {` at schema line 133, column 9.

Attachments: `image.png`, `image-1.png` (duplicate captures of the same state).

---

## 5. Resolution

1. `ControllerGenerator` now adds a generated-line mapping for each custom endpoint, pointing to that endpoint’s source location.
2. `GenerationManifest.findMapping` now prefers the specific custom-endpoint mapping over a broad controller mapping and returns no schema mapping for generated lines outside all mapped ranges. This prevents unrelated javac errors from defaulting to `crud {`.
3. Added a Feature 27 regression test covering custom endpoint mapping and unmapped generated lines.
4. The checked-in TaskFlow schema validates, and its current generated controller has one `updateStatus` declaration. The screenshot’s duplicate signature still needs the exact input schema/generated source to diagnose.
5. Fixed the related custom create-action behavior shown in the follow-up screenshot: generated services now expose and use their model-named repository field (for example, `commentRepository`), matching the name emitted by `create Comment`.
6. When a non-void custom action binds `var x = create CurrentModel { ... }` and has no explicit return, the service now returns that created entity through the endpoint’s response mapper. It no longer falls through to `findAll().findFirst()` and return an unrelated existing row.
7. Fixed the duplicate repository shown in the Project screenshot. While collecting repositories for `create ProjectMembers`, the generator followed `ProjectMembers.project` back to `Project` and added `ProjectRepository` to the extra dependencies even though `ProjectServiceImpl` already injects it as its primary repository. The generator now excludes the service's current model from extra repositories.

---

---

## 6. Verification

- Feature 27 traceability harness: **13 passed, 0 failed**, including regression coverage for response DTO mapping, repository naming, and a created entity that relates back to the service model.
- `zenith-exemples/taskflow/main.zen`: **100% valid; 4 models and 3 enums**.
- Generated TaskFlow controller: one `updateStatus` and one `updatePriority` declaration.
- The latest source compilation succeeded, but `build-cli.bat` could not replace `bin/zenith.jar` because the running Zenith Studio process has the jar open. Feature 27 passed against the newly compiled `target/classes`; the packaged jar will need rebuilding after Studio releases its file lock.

## 7. Remaining Reproduction Detail

To investigate the duplicate method captured in the screenshot, retain/provide the exact `.zen` input and generated `TaskController.java` from that failed run. The screenshot and current checked-in schema differ in reproducibility, so the duplicate-method cause cannot be safely attributed to a specific generator feature yet.

