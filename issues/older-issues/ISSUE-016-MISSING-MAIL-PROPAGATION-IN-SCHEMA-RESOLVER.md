# ISSUE-016: Missing `allMails` Propagation in `SchemaResolver.java` Causes "Referenced mail contract does not exist" in Zenith Studio

**Issue ID**: `ISSUE-016`  
**Severity**: **P0 (Critical Validation Blocker & False Semantic Error in Studio)**  
**Status**: **RESOLVED & VERIFIED**  
**Discovered**: November 2026  
**Subsystems**: `dev.zenith.parser.SchemaResolver`  
**Reporter**: Zenith Studio Editor / Semantic Analyzer  

---

## 1. Problem Description & Symptoms

When authoring a schema in Zenith Studio that defines top-level `mail` contracts and triggers them via `send` statements inside endpoint actions:

```zenith
mail OrderConfirmation(order: Order, customer: Customer) {
    to: customer.email
    subject: "Order #" + order.orderNumber + " Confirmed"
    template: "emails/order-confirmation.html"
}

model Order {
    // ...
    endpoints {
        post "/{id}/checkout" -> OrderResponse {
            action: {
                status = OrderStatus.CONFIRMED
                send OrderConfirmation(order = entity, customer = entity.customer)
            }
        }
    }
}
```

Zenith Studio red-highlights the `send OrderConfirmation(...)` statement with a semantic error banner:

```
[schema.zen:113] Semantic error: Referenced mail contract 'OrderConfirmation' does not exist in endpoint 'POST /checkout'
```

Even though `mail OrderConfirmation(...)` is clearly declared directly in the schema!

---

## 2. Root Cause Analysis

In [`SchemaResolver.java`](file:///c:/Users/wergh/Desktop/apiRestLanguage/src/main/java/dev/zenith/parser/SchemaResolver.java):

1. The `Parser` accurately parsed top-level `mail` declarations into `List<MailDef> mails` on the AST.
2. However, in both `resolveSource(...)` (used by Zenith Studio when editing schemas) and `resolveFile(...)` (used by the CLI and compiler when loading schemas):
   ```java
   List<EnumDef> allEnums = new ArrayList<>(rootFile.getEnums());
   List<ModelDef> allModels = new ArrayList<>(rootFile.getModels());
   List<DtoDef> allDtos = new ArrayList<>(rootFile.getDtos());
   List<SeedDef> allSeeds = new ArrayList<>(rootFile.getSeeds());
   List<JobDef> allJobs = new ArrayList<>(rootFile.getJobs());
   // MISSING: List<MailDef> allMails!
   List<ImportDef> allImports = new ArrayList<>(rootFile.getImports());
   ```
3. When constructing the final unified `ZenithFile`:
   ```java
   return new ZenithFile(finalConfig, allEnums, allModels, allDtos, allSeeds, allJobs, allImports, hasConfig);
   ```
   It was calling the deprecated constructor overload that passed `Collections.emptyList()` for `mails`:
   ```java
   public ZenithFile(AppConfig config, List<EnumDef> enums, List<ModelDef> models, List<DtoDef> dtos,
           List<SeedDef> seeds, List<JobDef> jobs, List<ImportDef> imports, boolean explicitConfig) {
       this(config, enums, models, dtos, seeds, jobs, Collections.emptyList(), imports, explicitConfig);
   }
   ```
4. Consequently, whenever the AST was passed to `SemanticAnalyzer.java`, `file.getMails()` was **always empty** (`[]`) and `file.findMail("OrderConfirmation")` returned `null`, triggering the false positive `AnalysisException`.

---

## 3. Remediation Applied

In [`SchemaResolver.java`](file:///c:/Users/wergh/Desktop/apiRestLanguage/src/main/java/dev/zenith/parser/SchemaResolver.java):

1. Added `List<MailDef> allMails` initialization in both `resolveSource` and `resolveFile`:
   ```java
   List<MailDef> allMails = new ArrayList<>(rootFile.getMails());
   ```
2. Aggregated imported mail contracts:
   ```java
   allMails.addAll(importedFile.getMails());
   ```
3. Passed `allMails` to the primary `ZenithFile` constructor:
   ```java
   return new ZenithFile(finalConfig, allEnums, allModels, allDtos, allSeeds, allJobs, allMails, allImports, hasConfig);
   ```

---

## 4. Verification

1. Rebuilt CLI `bin/zenith.jar`.
2. Validated `schema.zen` containing `mail OrderConfirmation` and `send OrderConfirmation`:
   ```
   >> Validating schema.zen...
   >> Schema is 100% valid!
      Found 4 models and 2 enums.
   ```
3. The false positive `Semantic error: Referenced mail contract 'OrderConfirmation' does not exist` is completely eliminated.

