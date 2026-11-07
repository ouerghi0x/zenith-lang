package dev.zenith.analyzer;

import dev.zenith.ast.*;

import java.util.*;

public class SemanticAnalyzer {

    public ZenithFile analyze(ZenithFile file) {
        if (file.getConfig() != null) {
            Map<String, Object> props = file.getConfig().getProperties();
            boolean hasExplicitAuth = file.hasAuth();
            
            boolean declaresJwtSecret = props.keySet().stream().anyMatch(k -> k.equalsIgnoreCase("jwtSecret"));
            boolean declaresJwtExpiration = props.keySet().stream().anyMatch(k -> k.equalsIgnoreCase("jwtExpiration"));
            
            if (!hasExplicitAuth && (declaresJwtSecret || declaresJwtExpiration)) {
                throw new AnalysisException("Configuration 'jwtSecret' and 'jwtExpiration' require authentication to be enabled ('auth: jwt').");
            }
            
            if (declaresJwtSecret) {
                String secret = file.getConfig().getJwtSecret();
                if (secret == null || secret.trim().length() < 32) {
                    throw new AnalysisException("Configuration 'jwtSecret' must be at least 256 bits (32 characters) for secure HMAC-SHA256 signing.");
                }
            }
        }

        Set<String> modelNames = new HashSet<>();
        Set<String> enumNames = new HashSet<>();
        Set<String> dtoNames = new HashSet<>();

        for (DtoDef d : file.getDtos()) {
            if (!dtoNames.add(d.getName())) {
                throw new AnalysisException("Duplicate dto defined: '" + d.getName() + "'", d.getSourceFile(), d.getSourceLine(), 1);
            }
            Set<String> dtoFieldNames = new HashSet<>();
            for (FieldDef f : d.getFields()) {
                if (!dtoFieldNames.add(f.getName().toLowerCase())) {
                    throw new AnalysisException("Duplicate field '" + f.getName() + "' in dto '" + d.getName() + "'", d.getSourceFile(), d.getSourceLine(), 1);
                }
            }
        }

        for (EnumDef e : file.getEnums()) {
            if (!enumNames.add(e.getName())) {
                throw new AnalysisException("Duplicate enum defined: '" + e.getName() + "'", e.getSourceFile(), e.getSourceLine(), 1);
            }
        }

        for (ModelDef m : file.getModels()) {
            if (!modelNames.add(m.getName())) {
                throw new AnalysisException("Duplicate model defined: '" + m.getName() + "'", m.getSourceFile(), m.getSourceLine(), 1);
            }
        }

        List<ModelDef> validatedModels = new ArrayList<>();
        Map<String, List<FieldDef>> modelFieldsMap = new HashMap<>();
        for (ModelDef model : file.getModels()) {
            List<FieldDef> fields = new ArrayList<>(model.getFields());
            Set<String> fieldNames = new HashSet<>();

            boolean hasId = false;
            for (FieldDef f : fields) {
                if (!fieldNames.add(f.getName().toLowerCase())) {
                    throw new AnalysisException(
                            "Duplicate field '" + f.getName() + "' in model '" + model.getName() + "'",
                            model.getSourceFile(), model.getSourceLine(), 1);
                }
                if (f.hasAnnotation("id")) {
                    hasId = true;
                }
            }

            // Auto-inject id if omitted
            if (!hasId) {
                List<AnnotationDef> idAnns = new ArrayList<>();
                idAnns.add(new AnnotationDef("id"));
                idAnns.add(new AnnotationDef("auto"));
                FieldDef autoId = new FieldDef("id", TypeRef.simple("Long"), false, idAnns, null);
                fields.add(0, autoId);
            }

            // Validate @file and @image field types
            for (FieldDef field : fields) {
                if (field.hasAnnotation("file") || field.hasAnnotation("image")) {
                    String tName = field.getType().getName();
                    if (!"String".equalsIgnoreCase(tName)) {
                        throw new AnalysisException("Field '" + field.getName() + "' in model '" + model.getName()
                                + "' annotated with @" + (field.hasAnnotation("image") ? "image" : "file")
                                + " must be of type String (to store file key/URL)",
                                model.getSourceFile(), model.getSourceLine(), 1);
                    }
                }
            }

            // Validate relations
            for (RelationDef rel : model.getRelations()) {
                if (!modelNames.contains(rel.getTargetModel())) {
                    throw new AnalysisException("Model '" + model.getName()
                            + "' references unknown relation target model '" + rel.getTargetModel() + "'",
                            model.getSourceFile(), model.getSourceLine(), 1);
                }
            }

            // Validate crud search/filter/sort fields
            CrudConfig crud = model.getCrudConfig();
            if (crud != null) {
                String crudFile = crud.getSourceFile() != null ? crud.getSourceFile() : model.getSourceFile();
                int crudLine = crud.getSourceLine() > 0 ? crud.getSourceLine() : model.getSourceLine();
                for (String sf : crud.getSearchFields()) {
                    if (findFieldCaseInsensitive(fields, sf) == null) {
                        throw new AnalysisException("Search field '" + sf + "' in crud config does not exist on model '"
                                + model.getName() + "'", crudFile, crudLine, 1);
                    }
                }
                for (String ff : crud.getFilterFields()) {
                    if (findFieldCaseInsensitive(fields, ff) == null && !isRelation(model.getRelations(), ff)) {
                        throw new AnalysisException(
                                "Filter field '" + ff + "' does not exist on model '" + model.getName() + "'",
                                crudFile, crudLine, 1);
                    }
                }
            }

            // Validate custom endpoint bodies, return types & action model references
            for (CustomEndpointDef endpoint : model.getCustomEndpoints()) {
                String epFile = endpoint.getSourceFile() != null ? endpoint.getSourceFile() : model.getSourceFile();
                int epLine = endpoint.getSourceLine() > 0 ? endpoint.getSourceLine() : model.getSourceLine();
                if (endpoint.hasBody()) {
                    String bt = endpoint.getBodyType().trim();
                    if (containsIgnoreCase(modelNames, bt)) {
                        String canonicalModel = findMatchIgnoreCase(modelNames, bt);
                        throw new AnalysisException("Cannot use entity model '" + canonicalModel
                                + "' directly as request body in REST endpoint '" + endpoint.getMethod() + " " + endpoint.getPath()
                                + "' on model '" + model.getName()
                                + "'. REST APIs cannot bind directly to JPA entities; model DTOs always end with 'Request' or 'Response' (use 'Create"
                                + canonicalModel + "Request', 'Update" + canonicalModel + "Request', or a custom 'dto').",
                                epFile, epLine, 1);
                    }
                    if (!containsIgnoreCase(dtoNames, bt) && !isAutoGeneratedModelDto(bt, modelNames)) {
                        throw new AnalysisException("Endpoint '" + endpoint.getMethod() + " " + endpoint.getPath()
                                + "' in model '" + model.getName() + "' references unknown request body type '" + bt
                                + "'. Model DTOs always end with '<ModelName>Response' or 'Create<ModelName>Request' / 'Update<ModelName>Request', or declare 'dto "
                                + bt + " { ... }'.", epFile, epLine, 1);
                    }
                }

                String rawRet = endpoint.getReturnType() != null ? endpoint.getReturnType().trim() : "Void";
                if (!rawRet.isEmpty() && !"Void".equalsIgnoreCase(rawRet)) {
                    String baseRet = rawRet;
                    if (rawRet.startsWith("List<") && rawRet.endsWith(">")) {
                        baseRet = rawRet.substring(5, rawRet.length() - 1).trim();
                    }
                    if (containsIgnoreCase(modelNames, baseRet)) {
                        String canonicalModel = findMatchIgnoreCase(modelNames, baseRet);
                        String suggestedDto = canonicalModel + "Response";
                        String suggestedReturn = rawRet.startsWith("List<") ? ("List<" + suggestedDto + ">") : suggestedDto;
                        throw new AnalysisException("Cannot return entity model '" + canonicalModel
                                + "' directly in REST endpoint '" + endpoint.getMethod() + " " + endpoint.getPath()
                                + "' on model '" + model.getName()
                                + "'. REST APIs cannot expose JPA entities as responses; model DTOs always end with '<ModelName>Response' (use '-> "
                                + suggestedReturn + "', a custom 'dto', or '-> Void').",
                                epFile, epLine, 1);
                    }
                    if (!containsIgnoreCase(dtoNames, baseRet)
                            && !isAutoGeneratedModelDto(baseRet, modelNames)
                            && !isScalarReturnType(baseRet)) {
                        throw new AnalysisException("Endpoint '" + endpoint.getMethod() + " " + endpoint.getPath()
                                + "' on model '" + model.getName() + "' references unknown return type '" + rawRet
                                + "'. Model DTOs always end with '<ModelName>Response' (e.g. '" + model.getName()
                                + "Response'), or use a declared 'dto' or 'Void'.",
                                epFile, epLine, 1);
                    }
                }

                for (String stmt : endpoint.getActionStatements()) {
                    validateActionStatement(stmt, model, file, modelNames, "endpoint '" + endpoint.getMethod() + " " + endpoint.getPath() + "'", epFile, epLine);
                }
            }

            ModelDef validatedModel = new ModelDef(model.getName(), fields, model.getRelations(), model.getCrudConfig(),
                    model.getCustomEndpoints());
            validatedModel.setSourceFile(model.getSourceFile());
            validatedModel.setSourceLine(model.getSourceLine());
            validatedModels.add(validatedModel);
            modelFieldsMap.put(model.getName(), fields);
        }
        for (SeedDef seed : file.getSeeds()) {
            ModelDef targetModel = null;
            for (ModelDef m : validatedModels) {
                if (m.getName().equalsIgnoreCase(seed.getEntityName())) {
                    targetModel = m;
                    break;
                }
            }
            if (targetModel == null) {
                throw new AnalysisException("Seed references unknown model '" + seed.getEntityName() + "'");
            }
            for (Map<String, Object> row : seed.getRows()) {
                for (String key : row.keySet()) {
                    FieldDef f = targetModel.findField(key);
                    RelationDef r = targetModel.findRelation(key);
                    if (f == null && r == null) {
                        throw new AnalysisException("Seed for model '" + targetModel.getName() + "' references unknown field '" + key + "'");
                    }
                    Object val = row.get(key);
                    if (val instanceof SeedRef) {
                        SeedRef ref = (SeedRef) val;
                        ModelDef refModel = null;
                        for (ModelDef m : validatedModels) {
                            if (m.getName().equalsIgnoreCase(ref.getModelName())) {
                                refModel = m;
                                break;
                            }
                        }
                        if (refModel == null) {
                            throw new AnalysisException("Seed reference points to unknown model '" + ref.getModelName() + "' in " + targetModel.getName() + "." + key);
                        }
                        if (r != null && !r.getTargetModel().equalsIgnoreCase(ref.getModelName())) {
                            throw new AnalysisException("Seed reference model '" + ref.getModelName() + "' does not match relation target '" + r.getTargetModel() + "' on field '" + key + "'");
                        }
                    }
                }
            }
        }
        Set<String> jobNames = new HashSet<>();
        for (JobDef j : file.getJobs()) {
            if (!jobNames.add(j.getName().toLowerCase())) {
                throw new AnalysisException("Duplicate background job defined: '" + j.getName() + "'", j.getSourceFile(), j.getSourceLine(), 1);
            }
            if (j.getCronExpression() == null && j.getFixedRate() == null) {
                throw new AnalysisException("Job '" + j.getName() + "' must specify either 'cron' or 'fixedRate'", j.getSourceFile(), j.getSourceLine(), 1);
            }
            for (String stmt : j.getActionStatements()) {
                validateActionStatement(stmt, null, file, modelNames, "job '" + j.getName() + "'", j.getSourceFile(), j.getSourceLine());
            }
        }

        // Validate all role references in crud configs and endpoint guards
        validateRoleReferences(file, validatedModels);

        return new ZenithFile(file.getConfig(), file.getEnums(), validatedModels, file.getDtos(), file.getSeeds(),
                file.getJobs(), file.getImports(), file.hasExplicitConfig());
    }

    private void validateRoleReferences(ZenithFile file, List<ModelDef> models) {
        Set<String> definedRoles = file.getAllDefinedRoles();

        for (ModelDef model : models) {
            CrudConfig crud = model.getCrudConfig();
            if (crud != null) {
                String srcFile = crud.getSourceFile() != null ? crud.getSourceFile() : model.getSourceFile();
                int srcLine = crud.getSourceLine() > 0 ? crud.getSourceLine() : model.getSourceLine();

                for (String role : crud.getDefaultRoles()) {
                    validateSingleRole(role, definedRoles, srcFile, srcLine, "crud configuration on model '" + model.getName() + "'");
                }
                for (Map.Entry<String, List<String>> entry : crud.getOperationRoles().entrySet()) {
                    String op = entry.getKey();
                    for (String role : entry.getValue()) {
                        validateSingleRole(role, definedRoles, srcFile, srcLine, "crud operation '" + op + "' on model '" + model.getName() + "'");
                    }
                }
            }

            for (CustomEndpointDef endpoint : model.getCustomEndpoints()) {
                String epFile = endpoint.getSourceFile() != null ? endpoint.getSourceFile() : model.getSourceFile();
                int epLine = endpoint.getSourceLine() > 0 ? endpoint.getSourceLine() : model.getSourceLine();

                for (GuardDef guard : endpoint.getGuards()) {
                    for (String role : guard.extractRoles()) {
                        validateSingleRole(role, definedRoles, epFile, epLine, "endpoint '" + endpoint.getMethod() + " " + endpoint.getPath() + "' on model '" + model.getName() + "'");
                    }
                }
            }
        }
    }

    private void validateSingleRole(String role, Set<String> definedRoles, String file, int line, String context) {
        if (role == null || role.trim().isEmpty()) return;
        String r = role.trim();
        if ("public".equalsIgnoreCase(r) || "authenticated".equalsIgnoreCase(r) || "auth".equalsIgnoreCase(r)) {
            return;
        }

        boolean found = false;
        for (String dr : definedRoles) {
            if (dr.equalsIgnoreCase(r)) {
                found = true;
                break;
            }
        }

        if (!found) {
            throw new AnalysisException("Role '" + r + "' referenced in " + context + " does not exist. Available roles: " + (definedRoles.isEmpty() ? "none" : String.join(", ", definedRoles)), file, line, 1);
        }
    }

    private void validateActionStatement(String stmt, ModelDef model, ZenithFile file, Set<String> modelNames, String context, String srcFile, int srcLine) {
        if (stmt == null || stmt.trim().isEmpty()) return;
        String s = stmt.trim();

        if (s.startsWith("create ")) {
            String rest = s.substring(7).trim();
            int brace = rest.indexOf('{');
            String targetModel = brace > 0 ? rest.substring(0, brace).trim() : rest;
            if (!modelNames.contains(targetModel)) {
                throw new AnalysisException("Action 'create' in " + context + " references unknown model '" + targetModel + "'", srcFile, srcLine, 1);
            }
            return;
        }

        if (s.startsWith("update ")) {
            String rest = s.substring(7).trim();
            int brace = rest.indexOf('{');
            String targetModel = brace > 0 ? rest.substring(0, brace).trim() : rest;
            if (targetModel.contains(" on ")) {
                targetModel = targetModel.substring(0, targetModel.indexOf(" on ")).trim();
            }
            if (!modelNames.contains(targetModel)) {
                throw new AnalysisException("Action 'update' in " + context + " references unknown model '" + targetModel + "'", srcFile, srcLine, 1);
            }
            return;
        }

        if (s.startsWith("notify") || s.matches("^notify\\s*\\(.*")) {
            validateExpressionFunctions(s, context, srcFile, srcLine);
            return;
        }

        if (s.startsWith("return ") || s.equals("return")) {
            if (s.startsWith("return ")) {
                String rest = s.substring(7).trim();
                int brace = rest.indexOf('{');
                if (brace > 0) {
                    String retType = rest.substring(0, brace).trim();
                    if (containsIgnoreCase(modelNames, retType)) {
                        String canonicalModel = findMatchIgnoreCase(modelNames, retType);
                        throw new AnalysisException("Cannot return entity model '" + canonicalModel
                                + "' directly in " + context
                                + ". REST APIs cannot expose JPA entities; use '" + canonicalModel
                                + "Response' or a custom 'dto' instead.", srcFile, srcLine, 1);
                    }
                }
            }
            validateExpressionFunctions(s, context, srcFile, srcLine);
            return;
        }

        if (s.contains("=") && !s.contains("==") && !s.contains("!=") && !s.contains("<=") && !s.contains(">=")) {
            String[] parts = s.split("=", 2);
            String left = parts[0].trim().replaceAll("\\s+", "");
            if (left.startsWith("entity.")) {
                left = left.substring(7);
            }
            if (model != null) {
                if (left.startsWith(model.getName().toLowerCase() + ".")) {
                    left = left.substring(model.getName().length() + 1);
                }
                if (model.findField(left) == null && model.findRelation(left) == null) {
                    throw new AnalysisException("Assignment in " + context + " targets unknown field or relation '" + left + "' on model '" + model.getName() + "'", srcFile, srcLine, 1);
                }
            }
            validateExpressionFunctions(parts[1], context, srcFile, srcLine);
            return;
        }

        throw new AnalysisException("Unsupported action statement in " + context + ": '" + s + "'. "
                + "Zenith action statements must be an assignment ('field = value'), 'create Model { ... }', 'update Model { ... }', 'notify(to, message)', or 'return ...'.", srcFile, srcLine, 1);
    }

    private void validateExpressionFunctions(String expr, String context, String srcFile, int srcLine) {
        if (expr == null) return;
        java.util.regex.Pattern fnPattern = java.util.regex.Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(");
        java.util.regex.Matcher m = fnPattern.matcher(expr);
        Set<String> supportedFns = Set.of(
            "now", "today",
            "daysago", "daysfromnow",
            "hoursago", "hoursfromnow",
            "minutesago", "minutesfromnow",
            "monthsago", "monthsfromnow",
            "yearsago", "yearsfromnow",
            "notify"
        );
        while (m.find()) {
            String fnName = m.group(1);
            if (fnName.startsWith("___STR_")) continue;
            if (fnName.equalsIgnoreCase("if") || fnName.equalsIgnoreCase("for") || fnName.equalsIgnoreCase("while")) continue;
            if (!supportedFns.contains(fnName.toLowerCase())) {
                throw new AnalysisException("Unsupported function '" + fnName + "(...)' in " + context + " expression: '" + expr.trim() + "'. "
                        + "Supported date/time functions: now(), today(), daysAgo(n), daysFromNow(n), hoursAgo(n), hoursFromNow(n), minutesAgo(n), minutesFromNow(n). Supported operations: notify(recipient, message).", srcFile, srcLine, 1);
            }
        }
    }

    private FieldDef findFieldCaseInsensitive(List<FieldDef> fields, String name) {
        for (FieldDef f : fields) {
            if (f.getName().equalsIgnoreCase(name)) {
                return f;
            }
        }
        return null;
    }

    private boolean isRelation(List<RelationDef> relations, String name) {
        for (RelationDef r : relations) {
            if (r.getFieldName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsIgnoreCase(Set<String> set, String target) {
        return findMatchIgnoreCase(set, target) != null;
    }

    private String findMatchIgnoreCase(Set<String> set, String target) {
        if (target == null) return null;
        for (String s : set) {
            if (s.equalsIgnoreCase(target)) {
                return s;
            }
        }
        return null;
    }

    private boolean isAutoGeneratedModelDto(String typeName, Set<String> modelNames) {
        if (typeName == null) return false;
        for (String m : modelNames) {
            if (typeName.equalsIgnoreCase(m + "Response")
                    || typeName.equalsIgnoreCase("Create" + m + "Request")
                    || typeName.equalsIgnoreCase("Update" + m + "Request")
                    || typeName.equalsIgnoreCase(m + "Request")) {
                return true;
            }
        }
        return false;
    }

    private boolean isScalarReturnType(String typeName) {
        if (typeName == null) return false;
        Set<String> scalars = Set.of(
            "string", "int", "integer", "long", "boolean", "bool",
            "double", "float", "decimal", "bigdecimal", "uuid",
            "datetime", "date", "void"
        );
        return scalars.contains(typeName.toLowerCase());
    }
}
