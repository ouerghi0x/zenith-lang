package dev.zenith.analyzer;

import dev.zenith.ast.*;

import java.util.*;

public class SemanticAnalyzer {

    private static final List<String> SUPPORTED_FIELD_ANNOTATIONS = Arrays.asList(
            "id", "auto", "unique", "index", "uniqueIndex",
            "password", "email", "role",
            "positive", "positiveOrZero", "negative", "negativeOrZero",
            "min", "max", "length", "pattern", "size",
            "autoNow", "autoUpdate",
            "text", "file", "image", "relation", "rateLimit",
            "softDelete", "audited", "encrypted", "tenant", "owner");

    public ZenithFile analyze(ZenithFile file) {
        validateStreams(file);
        if (file.getConfig() != null) {
            Map<String, Object> props = file.getConfig().getProperties();
            // This validation concerns whether JWT auth was declared in config.
            // ZenithFile.hasAuth() also requires a user model, which is a
            // separate generation capability and must not invalidate JWT
            // settings in schemas that do not define one yet.
            boolean hasExplicitAuth = file.getConfig().isAuthEnabled();

            if (file.getConfig().isOutboxEnabled() && file.getConfig().isFlywayEnabled()) {
                throw new AnalysisException(
                        "Configuration 'outbox: true' currently uses Spring Modulith JDBC schema initialization and cannot be combined with 'flyway: true'. Disable Flyway or disable the outbox.");
            }

            boolean declaresJwtSecret = props.keySet().stream().anyMatch(k -> k.equalsIgnoreCase("jwtSecret"));
            boolean declaresJwtExpiration = props.keySet().stream().anyMatch(k -> k.equalsIgnoreCase("jwtExpiration"));

            if (!hasExplicitAuth && (declaresJwtSecret || declaresJwtExpiration)) {
                throw new AnalysisException(
                        "Configuration 'jwtSecret' and 'jwtExpiration' require authentication to be enabled ('auth: jwt').");
            }

            if (declaresJwtSecret) {
                String rawSecret = file.getConfig().getRawJwtSecret();
                if (rawSecret == null || rawSecret.trim().isEmpty()) {
                    throw new AnalysisException("Configuration 'jwtSecret' cannot be empty.");
                }
            }
        }

        Set<String> modelNames = new HashSet<>();
        Set<String> enumNames = new HashSet<>();
        Set<String> dtoNames = new HashSet<>();

        for (DtoDef d : file.getDtos()) {
            if (!dtoNames.add(d.getName())) {
                throw new AnalysisException("Duplicate dto defined: '" + d.getName() + "'", d.getSourceFile(),
                        d.getSourceLine(), 1);
            }
            Set<String> dtoFieldNames = new HashSet<>();
            for (FieldDef f : d.getFields()) {
                if (!dtoFieldNames.add(f.getName().toLowerCase())) {
                    throw new AnalysisException("Duplicate field '" + f.getName() + "' in dto '" + d.getName() + "'",
                            d.getSourceFile(), d.getSourceLine(), 1);
                }
            }
        }

        for (EnumDef e : file.getEnums()) {
            if (!enumNames.add(e.getName())) {
                throw new AnalysisException("Duplicate enum defined: '" + e.getName() + "'", e.getSourceFile(),
                        e.getSourceLine(), 1);
            }
        }

        for (ModelDef m : file.getModels()) {
            if (!modelNames.add(m.getName())) {
                throw new AnalysisException("Duplicate model defined: '" + m.getName() + "'", m.getSourceFile(),
                        m.getSourceLine(), 1);
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

            // Validate field types & annotations
            for (FieldDef field : fields) {
                String tName = field.getType().getName();
                boolean isPrimitiveOrKnown = isScalarReturnType(tName)
                        || enumNames.contains(tName)
                        || modelNames.contains(tName)
                        || dtoNames.contains(tName)
                        || "text".equalsIgnoreCase(tName)
                        || "bytearray".equalsIgnoreCase(tName)
                        || "bytes".equalsIgnoreCase(tName);

                if (!isPrimitiveOrKnown) {
                    throw new AnalysisException("Field '" + field.getName() + "' in model '" + model.getName()
                            + "' has unknown type '" + tName
                            + "'. Did you mean a built-in type (String, Int, Long, DateTime, UUID, Boolean, Decimal) or an enum?",
                            model.getSourceFile(), model.getSourceLine(), 1);
                }

                // Validate all field annotations are supported
                for (AnnotationDef ann : field.getAnnotations()) {
                    String annName = ann.getName();
                    boolean supported = false;
                    for (String validAnn : SUPPORTED_FIELD_ANNOTATIONS) {
                        if (validAnn.equalsIgnoreCase(annName)) {
                            supported = true;
                            break;
                        }
                    }
                    if (!supported) {
                        int errLine = ann.getSourceLine() > 0 ? ann.getSourceLine() : model.getSourceLine();
                        String suggestion = closestMatch(annName, SUPPORTED_FIELD_ANNOTATIONS);
                        throw new AnalysisException("Unsupported field annotation '@" + annName + "' on field '"
                                + field.getName() + "' in model '" + model.getName() + "'."
                                + (suggestion != null ? " Did you mean '@" + suggestion + "'?" : "")
                                + " Supported annotations: @id, @auto, @unique, @index, @uniqueIndex, @email, @password, @role, @positive, @min, @max, @length, @pattern, @autoNow, @autoUpdate, @text, @file, @image, @relation.",
                                model.getSourceFile(), errLine, 1);
                    }
                }

                if (field.hasAnnotation("autoNow") || field.hasAnnotation("autoUpdate")) {
                    String simpleType = tName.toLowerCase();
                    if (!"datetime".equals(simpleType) && !"localdatetime".equals(simpleType)
                            && !"date".equals(simpleType) && !"localdate".equals(simpleType)) {
                        String annName = field.hasAnnotation("autoNow") ? "@autoNow" : "@autoUpdate";
                        throw new AnalysisException("Field '" + field.getName() + "' in model '" + model.getName()
                                + "' annotated with " + annName + " must be of type DateTime (or Date), found '" + tName
                                + "'.",
                                model.getSourceFile(), model.getSourceLine(), 1);
                    }
                }

                if (field.hasAnnotation("file") || field.hasAnnotation("image")) {
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
                                + "' directly as request body in REST endpoint '" + endpoint.getMethod() + " "
                                + endpoint.getPath()
                                + "' on model '" + model.getName()
                                + "'. REST APIs cannot bind directly to JPA entities; model DTOs always end with 'Request' or 'Response' (use 'Create"
                                + canonicalModel + "Request', 'Update" + canonicalModel
                                + "Request', or a custom 'dto').",
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
                        String suggestedReturn = rawRet.startsWith("List<") ? ("List<" + suggestedDto + ">")
                                : suggestedDto;
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

                Map<String, String> actionVariables = new HashMap<>();
                if (endpoint.hasBody()) actionVariables.put("body", endpoint.getBodyType());
                if (endpoint.getParamTypes() != null) {
                    actionVariables.putAll(endpoint.getParamTypes());
                }
                if (endpoint.getPath() != null) {
                    java.util.regex.Matcher pm = java.util.regex.Pattern.compile("\\{([a-zA-Z0-9_]+)\\}").matcher(endpoint.getPath());
                    while (pm.find()) {
                        String pName = pm.group(1);
                        if (!actionVariables.containsKey(pName)) {
                            actionVariables.put(pName, "Object");
                        }
                    }
                }
                var actionDefs = endpoint.getActionStatementDefs();
                for (int ai = 0; ai < endpoint.getActionStatements().size(); ai++) {
                    String stmt = endpoint.getActionStatements().get(ai);
                    ActionStatementDef ad = ai < actionDefs.size() ? actionDefs.get(ai) : null;
                    int stmtLine = ad != null && ad.getSourceLocation() != null ? ad.getSourceLocation().getStartLine()
                            : epLine;
                    validateActionStatement(stmt, model, file, modelNames,
                            "endpoint '" + endpoint.getMethod() + " " + endpoint.getPath() + "'", epFile, stmtLine,
                            actionVariables, endpoint.getBodyType());
                }

                if (endpoint.hasRateLimit()) {
                    RateLimitSpec rls = endpoint.getRateLimit();
                    if (rls.getCapacity() <= 0) {
                        throw new AnalysisException("Rate limit capacity must be greater than zero on endpoint '"
                                + endpoint.getMethod() + " " + endpoint.getPath() + "'", epFile, epLine, 1);
                    }
                }

                if (endpoint.hasCache()) {
                    String m = endpoint.getMethod().toUpperCase();
                    if (!"GET".equals(m)) {
                        throw new AnalysisException(
                                "Caching is only permitted on read operations (GET), cannot cache endpoint '"
                                        + endpoint.getMethod() + " " + endpoint.getPath() + "'.",
                                epFile, epLine, 1);
                    }
                }
            }

            if (model.getCrudConfig() != null && model.getCrudConfig().hasRateLimit()) {
                RateLimitSpec rls = model.getCrudConfig().getRateLimit();
                String crudFile = model.getCrudConfig().getSourceFile() != null ? model.getCrudConfig().getSourceFile()
                        : model.getSourceFile();
                int crudLine = model.getCrudConfig().getSourceLine() > 0 ? model.getCrudConfig().getSourceLine()
                        : model.getSourceLine();
                if (rls.getCapacity() <= 0) {
                    throw new AnalysisException(
                            "Rate limit capacity must be greater than zero on crud block for model '"
                                    + model.getName() + "'",
                            crudFile, crudLine, 1);
                }
            }

            ModelDef validatedModel = new ModelDef(model.getName(), fields, model.getRelations(), model.getCrudConfig(),
                    model.getCustomEndpoints(), model.getIndexes());
            validatedModel.setSourceFile(model.getSourceFile());
            validatedModel.setSourceLine(model.getSourceLine());
            validateModelIndexes(validatedModel, enumNames);
            validatedModels.add(validatedModel);
            modelFieldsMap.put(model.getName(), fields);
        }
        validateIndexNameCollisions(validatedModels);
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
                        throw new AnalysisException("Seed for model '" + targetModel.getName()
                                + "' references unknown field '" + key + "'");
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
                            throw new AnalysisException("Seed reference points to unknown model '" + ref.getModelName()
                                    + "' in " + targetModel.getName() + "." + key);
                        }
                        if (r != null && !r.getTargetModel().equalsIgnoreCase(ref.getModelName())) {
                            throw new AnalysisException(
                                    "Seed reference model '" + ref.getModelName() + "' does not match relation target '"
                                            + r.getTargetModel() + "' on field '" + key + "'");
                        }
                    }
                }

                // Check that required (non-optional, non-auto, non-relation) fields are
                // supplied in seed data
                for (FieldDef f : targetModel.getFields()) {
                    if (!f.isOptional() && !f.hasAnnotation("id") && !f.hasAnnotation("auto")
                            && !f.hasAnnotation("autoNow") && !f.hasAnnotation("autoUpdate")
                            && !f.hasAnnotation("password") && f.getDefaultValue() == null) {
                        boolean found = false;
                        for (String k : row.keySet()) {
                            if (k.equalsIgnoreCase(f.getName())) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            throw new AnalysisException("Seed data for model '" + targetModel.getName()
                                    + "' is missing required field '" + f.getName() + "' (" + f.getType().getName()
                                    + "). Non-optional fields without default values must be supplied in seed definitions to prevent database insert crashes.",
                                    targetModel.getSourceFile(), targetModel.getSourceLine(), 1);
                        }
                    }
                }
            }
        }
        Set<String> jobNames = new HashSet<>();
        for (JobDef j : file.getJobs()) {
            if (!jobNames.add(j.getName().toLowerCase())) {
                throw new AnalysisException("Duplicate background job defined: '" + j.getName() + "'",
                        j.getSourceFile(), j.getSourceLine(), 1);
            }
            if (j.getCronExpression() == null && j.getFixedRate() == null) {
                throw new AnalysisException("Job '" + j.getName() + "' must specify either 'cron' or 'fixedRate'",
                        j.getSourceFile(), j.getSourceLine(), 1);
            }
            Map<String, String> jobVariables = new HashMap<>();
            for (String stmt : j.getActionStatements()) {
                validateActionStatement(stmt, null, file, modelNames, "job '" + j.getName() + "'", j.getSourceFile(),
                        j.getSourceLine(), jobVariables);
            }
        }

        // Validate all role references in crud configs and endpoint guards
        validateRoleReferences(file, validatedModels);

        // Validate Mail definitions & send statements
        validateMailDefinitions(file, modelNames);

        return new ZenithFile(file.getConfig(), file.getEnums(), validatedModels, file.getDtos(), file.getSeeds(),
                file.getJobs(), file.getMails(), file.getSockets(), file.getEvents(), file.getStreams(), file.getConsumers(), file.getImports(), file.hasExplicitConfig());
    }

    private void validateStreams(ZenithFile file) {
        Set<String> eventNames = new HashSet<>();
        for (EventDef event : file.getEvents()) {
            if (!eventNames.add(event.getName().toLowerCase())) throw new AnalysisException("Duplicate event defined: '" + event.getName() + "'", event.getSourceFile(), event.getSourceLine(), 1);
            Set<String> fields = new HashSet<>();
            for (FieldDef field : event.getFields()) {
                if (!fields.add(field.getName().toLowerCase())) throw new AnalysisException("Duplicate field '" + field.getName() + "' in event '" + event.getName() + "'", event.getSourceFile(), event.getSourceLine(), 1);
                if (!field.getAnnotations().isEmpty()) throw new AnalysisException("Event fields cannot use persistence or mutable-value annotations", event.getSourceFile(), event.getSourceLine(), 1);
                if (field.getType().isList() || "list".equalsIgnoreCase(field.getType().getName())) throw new AnalysisException("Collection fields are not supported in event contracts", event.getSourceFile(), event.getSourceLine(), 1);
                String type = field.getType().getName();
                if (!isScalarReturnType(type) && !"DateTime".equalsIgnoreCase(type) && file.findEnum(type) == null) throw new AnalysisException("Unsupported event field type '" + type + "'", event.getSourceFile(), event.getSourceLine(), 1);
            }
        }
        Set<String> streamNames = new HashSet<>();
        Set<String> brokers = new HashSet<>();
        String appBroker = file.getConfig().getBroker();
        for (StreamDef stream : file.getStreams()) {
            if (!streamNames.add(stream.getName().toLowerCase())) throw new AnalysisException("Duplicate stream defined: '" + stream.getName() + "'", stream.getSourceFile(), stream.getSourceLine(), 1);
            String broker = stream.getBroker() != null ? stream.getBroker() : appBroker;
            if (broker == null || !(broker.equalsIgnoreCase("kafka") || broker.equalsIgnoreCase("rabbitmq"))) throw new AnalysisException("Stream '" + stream.getName() + "' requires broker kafka or rabbitmq", stream.getSourceFile(), stream.getSourceLine(), 1);
            brokers.add(broker.toLowerCase());
            if (stream.getTopic() == null || !stream.getTopic().matches("[A-Za-z0-9._-]+")) throw new AnalysisException("Invalid or missing stream destination name", stream.getSourceFile(), stream.getSourceLine(), 1);
            if (stream.getPayload() == null || file.findEvent(stream.getPayload()) == null) throw new AnalysisException("Stream payload must reference a declared event", stream.getSourceFile(), stream.getSourceLine(), 1);
            if (stream.getPartitions() != null && (stream.getPartitions() < 1 || !broker.equalsIgnoreCase("kafka"))) throw new AnalysisException("Partitions must be positive and are supported only by Kafka", stream.getSourceFile(), stream.getSourceLine(), 1);
            if (stream.getBroker() != null && appBroker != null && !stream.getBroker().equalsIgnoreCase(appBroker)) throw new AnalysisException("Mixed brokers in one service are not supported", stream.getSourceFile(), stream.getSourceLine(), 1);
        }
        if (brokers.size() > 1) throw new AnalysisException("Mixed brokers in one service are not supported");
        Set<String> consumerNames = new HashSet<>();
        for (StreamConsumerDef consumer : file.getConsumers()) {
            String contextFile = consumer.getSourceFile();
            int contextLine = consumer.getSourceLine();
            if (!consumerNames.add(consumer.getName().toLowerCase())) throw new AnalysisException("Duplicate stream consumer defined: '" + consumer.getName() + "'", contextFile, contextLine, 1);
            StreamDef source = null;
            for (StreamDef candidate : file.getStreams()) if (candidate.getName().equalsIgnoreCase(consumer.getStream())) source = candidate;
            if (source == null) throw new AnalysisException("Consumer '" + consumer.getName() + "' must reference a declared stream with 'from'", contextFile, contextLine, 1);
            if (consumer.getGroup() == null || !consumer.getGroup().matches("[A-Za-z0-9._-]+")) throw new AnalysisException("Consumer group must be a non-empty broker-safe name", contextFile, contextLine, 1);
            if (consumer.getConcurrency() != null && consumer.getConcurrency() < 1) throw new AnalysisException("Consumer concurrency must be positive", contextFile, contextLine, 1);
            if (consumer.getMaxAttempts() == null || consumer.getMaxAttempts() < 1) throw new AnalysisException("Retry maxAttempts must be positive", contextFile, contextLine, 1);
            if (consumer.getBackoffMs() == null || consumer.getBackoffMs() < 1 || consumer.getMaxBackoffMs() == null || consumer.getMaxBackoffMs() < consumer.getBackoffMs()) throw new AnalysisException("Retry backoff must be positive and maxBackoff must be at least backoff", contextFile, contextLine, 1);
            if (consumer.getBackoffMultiplier() == null || consumer.getBackoffMultiplier() < 1) throw new AnalysisException("Retry multiplier must be at least 1", contextFile, contextLine, 1);
            if (consumer.getDeadLetter() != null && !consumer.getDeadLetter().matches("[A-Za-z0-9._-]+")) throw new AnalysisException("Invalid dead-letter destination", contextFile, contextLine, 1);
            if (consumer.getActions().isEmpty()) throw new AnalysisException("Consumer requires at least one action", contextFile, contextLine, 1);
            EventDef payload = file.findEvent(source.getPayload());
            for (String action : consumer.getActions()) {
                java.util.regex.Matcher log = java.util.regex.Pattern.compile("^log payload\\.([A-Za-z_][A-Za-z0-9_]*)$").matcher(action);
                if (!log.matches()) throw new AnalysisException("Unsupported consumer action; expected 'log payload.field'", contextFile, contextLine, 1);
                boolean found = false;
                for (FieldDef field : payload.getFields()) if (field.getName().equalsIgnoreCase(log.group(1))) found = true;
                if (!found) throw new AnalysisException("Consumer action references unknown payload field '" + log.group(1) + "'", contextFile, contextLine, 1);
            }
        }
    }

    private void validateRoleReferences(ZenithFile file, List<ModelDef> models) {
        Set<String> definedRoles = file.getAllDefinedRoles();

        for (ModelDef model : models) {
            CrudConfig crud = model.getCrudConfig();
            if (crud != null) {
                String srcFile = crud.getSourceFile() != null ? crud.getSourceFile() : model.getSourceFile();
                int srcLine = crud.getSourceLine() > 0 ? crud.getSourceLine() : model.getSourceLine();

                for (String role : crud.getDefaultRoles()) {
                    validateSingleRole(role, definedRoles, srcFile, srcLine,
                            "crud configuration on model '" + model.getName() + "'");
                }
                for (Map.Entry<String, List<String>> entry : crud.getOperationRoles().entrySet()) {
                    String op = entry.getKey();
                    for (String role : entry.getValue()) {
                        validateSingleRole(role, definedRoles, srcFile, srcLine,
                                "crud operation '" + op + "' on model '" + model.getName() + "'");
                    }
                }
            }

            for (CustomEndpointDef endpoint : model.getCustomEndpoints()) {
                String epFile = endpoint.getSourceFile() != null ? endpoint.getSourceFile() : model.getSourceFile();
                int epLine = endpoint.getSourceLine() > 0 ? endpoint.getSourceLine() : model.getSourceLine();

                for (String role : endpoint.getRoles()) {
                    validateSingleRole(role, definedRoles, epFile, epLine, "endpoint '" + endpoint.getMethod() + " "
                            + endpoint.getPath() + "' on model '" + model.getName() + "'");
                }

                for (GuardDef guard : endpoint.getGuards()) {
                    for (String role : guard.extractRoles()) {
                        validateSingleRole(role, definedRoles, epFile, epLine, "endpoint '" + endpoint.getMethod() + " "
                                + endpoint.getPath() + "' on model '" + model.getName() + "'");
                    }
                }
            }
        }
    }

    private void validateSingleRole(String role, Set<String> definedRoles, String file, int line, String context) {
        if (role == null || role.trim().isEmpty())
            return;
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
            throw new AnalysisException(
                    "Role '" + r + "' referenced in " + context + " does not exist. Available roles: "
                            + (definedRoles.isEmpty() ? "none" : String.join(", ", definedRoles)),
                    file, line, 1);
        }
    }

    private static final Set<String> RESERVED_ACTION_VARS = Set.of("entity", "body", "currentUser", "currentUsername",
            "id", "this", "request", "response");

    private void validateActionStatement(String stmt, ModelDef model, ZenithFile file, Set<String> modelNames,
            String context, String srcFile, int srcLine, Map<String, String> localVars) {
        validateActionStatement(stmt, model, file, modelNames, context, srcFile, srcLine, localVars, null);
    }

    private void validateActionStatement(String stmt, ModelDef model, ZenithFile file, Set<String> modelNames,
            String context, String srcFile, int srcLine, Map<String, String> localVars, String bodyType) {
        if (stmt == null || stmt.trim().isEmpty())
            return;
        String s = stmt.trim();

        if (s.startsWith("fail(")) {
            java.util.regex.Matcher fail = java.util.regex.Pattern
                    .compile("^fail\\(\\s*\"(?:\\\\.|[^\"])*\"\\s*,\\s*(\\d+)\\s*,\\s*\"(?:\\\\.|[^\"])*\"\\s*\\)$")
                    .matcher(s);
            if (!fail.matches()) throw new AnalysisException("Invalid fail statement; expected fail(code, status, message)", srcFile, srcLine, 1);
            int status = Integer.parseInt(fail.group(1));
            if (status < 400 || status > 599) throw new AnalysisException("HTTP status code for fail must be between 400 and 599", srcFile, srcLine, 1);
            return;
        }

        if (s.startsWith("try {")) {
            validateTryCatchStatement(s, model, file, modelNames, context, srcFile, srcLine, localVars);
            return;
        }

        if (s.startsWith("for ")) {
            validateForEachStatement(s, model, file, modelNames, context, srcFile, srcLine, localVars, bodyType);
            return;
        }

        if (s.startsWith("publish ")) {
            validatePublishStatement(s, model, file, localVars, context, srcFile, srcLine);
            return;
        }

        if (s.startsWith("var ")) {
            String rest = s.substring(4).trim();
            int eqIdx = rest.indexOf('=');
            if (eqIdx < 0) {
                throw new AnalysisException(
                        "Action variable binding in " + context
                                + " must have '=' assignment (e.g. 'var x = create Model { ... }')",
                        srcFile, srcLine, 1);
            }
            String varName = rest.substring(0, eqIdx).trim();
            if (varName.isEmpty()) {
                throw new AnalysisException("Missing variable name after 'var' in " + context, srcFile, srcLine, 1);
            }
            if (RESERVED_ACTION_VARS.contains(varName)) {
                throw new AnalysisException(
                        "Variable name '" + varName + "' in " + context + " collides with a reserved keyword", srcFile,
                        srcLine, 1);
            }
            if (localVars.containsKey(varName) && !"body".equals(varName)) {
                throw new AnalysisException("Duplicate variable declaration '" + varName + "' in " + context, srcFile,
                        srcLine, 1);
            }
            String rightExpr = rest.substring(eqIdx + 1).trim();
            String targetModel = null;
            if (rightExpr.startsWith("create ")) {
                String cRest = rightExpr.substring(7).trim();
                int brace = cRest.indexOf('{');
                targetModel = brace > 0 ? cRest.substring(0, brace).trim() : cRest;
            } else if (rightExpr.startsWith("update ")) {
                String uRest = rightExpr.substring(7).trim();
                int brace = uRest.indexOf('{');
                targetModel = brace > 0 ? uRest.substring(0, brace).trim() : uRest;
                if (targetModel.contains(" on ")) {
                    targetModel = targetModel.substring(0, targetModel.indexOf(" on ")).trim();
                }
            } else {
                throw new AnalysisException("Variable binding '" + varName + "' in " + context
                        + " must be initialized with 'create' or 'update' expression", srcFile, srcLine, 1);
            }
            if (!modelNames.contains(targetModel)) {
                throw new AnalysisException("Action '" + (rightExpr.startsWith("create") ? "create" : "update")
                        + "' in " + context + " references unknown model '" + targetModel + "'", srcFile, srcLine, 1);
            }

            // Check for self-referential initialization in the right-hand expression
            if (rightExpr.matches(".*\\b" + java.util.regex.Pattern.quote(varName) + "\\b.*")) {
                throw new AnalysisException(
                        "Self-referential or circular reference to variable '" + varName + "' in " + context, srcFile,
                        srcLine, 1);
            }

            validatePropertyAccesses(rightExpr, localVars, file, context, srcFile, srcLine);
            localVars.put(varName, targetModel);
            return;
        }

        if (s.startsWith("create ")) {
            String rest = s.substring(7).trim();
            int brace = rest.indexOf('{');
            String targetModel = brace > 0 ? rest.substring(0, brace).trim() : rest;
            if (!modelNames.contains(targetModel)) {
                throw new AnalysisException(
                        "Action 'create' in " + context + " references unknown model '" + targetModel + "'", srcFile,
                        srcLine, 1);
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
                throw new AnalysisException(
                        "Action 'update' in " + context + " references unknown model '" + targetModel + "'", srcFile,
                        srcLine, 1);
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
            validatePropertyAccesses(s, localVars, file, context, srcFile, srcLine);
            return;
        }

        if (s.startsWith("send ") || s.matches("^send\\s+[A-Za-z0-9_]+\\s*\\(.*")) {
            validateSendActionStatement(s, file, context, srcFile, srcLine);
            return;
        }

        if (s.startsWith("broadcast ") || s.matches("^broadcast\\s+.*")) {
            validateExpressionFunctions(s, context, srcFile, srcLine);
            return;
        }

        if (s.startsWith("sendToUser ") || s.matches("^sendToUser\\s*\\(.*")) {
            // Peer-to-peer WebSocket dispatch statement: sendToUser (recipientId, "/queue/messages") { ... }
            return;
        }

        if (s.contains("=") && !s.contains("==") && !s.contains("!=") && !s.contains("<=") && !s.contains(">=")) {
            String[] parts = s.split("=", 2);
            String left = parts[0].trim().replaceAll("\\s+", "");
            if (localVars.containsKey(left) && localVars.get(left).startsWith("@loop:")) {
                throw new AnalysisException("Cannot assign to loop variable '" + left + "' in " + context, srcFile, srcLine, 1);
            }
            if (left.startsWith("entity.")) {
                left = left.substring(7);
            }
            if (model != null) {
                if (left.startsWith(model.getName().toLowerCase() + ".")) {
                    left = left.substring(model.getName().length() + 1);
                }
                if (model.findField(left) == null && model.findRelation(left) == null) {
                    throw new AnalysisException("Assignment in " + context + " targets unknown field or relation '"
                            + left + "' on model '" + model.getName() + "'", srcFile, srcLine, 1);
                }
            }
            validatePropertyAccesses(parts[1], localVars, file, context, srcFile, srcLine);
            validateExpressionFunctions(parts[1], context, srcFile, srcLine);
            validateEnumConstants(left, parts[1], model, file, context, srcFile, srcLine, localVars);
            return;
        }

        throw new AnalysisException("Unsupported action statement in " + context + ": '" + s + "'. "
                + "Zenith action statements must be an assignment ('field = value'), 'create Model { ... }', 'update Model { ... }', 'send MailName(...)', 'notify(to, message)', or 'return ...'.",
                srcFile, srcLine, 1);
    }

    private void validateForEachStatement(String source, ModelDef model, ZenithFile file, Set<String> modelNames,
            String context, String srcFile, int srcLine, Map<String, String> localVars, String bodyType) {
        java.util.regex.Matcher header = java.util.regex.Pattern
                .compile("^for\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*(.*?)\\s*\\{", java.util.regex.Pattern.DOTALL)
                .matcher(source.trim());
        if (!header.find()) throw new AnalysisException("Invalid for loop; expected for item : items { ... }", srcFile, srcLine, 1);
        String variable = header.group(1);
        String iterable = header.group(2).trim();
        if (RESERVED_ACTION_VARS.contains(variable) || localVars.containsKey(variable)) {
            throw new AnalysisException("Loop variable '" + variable + "' shadows a reserved or existing action variable", srcFile, srcLine, 1);
        }
        int open = header.end() - 1;
        int close = matchingActionBrace(source, open);
        if (close < 0 || !source.substring(close + 1).trim().replace(";", "").isEmpty()) {
            throw new AnalysisException("Invalid or unclosed for loop body", srcFile, srcLine, 1);
        }

        String elementType = null;
        java.util.regex.Matcher root = java.util.regex.Pattern
                .compile("^([A-Za-z_][A-Za-z0-9_]*)(?:\\.([A-Za-z_][A-Za-z0-9_]*))?$")
                .matcher(iterable.replaceAll("\\s*\\.\\s*", "."));
        if (root.matches()) {
            String owner = root.group(1);
            if ("body".equals(owner) && bodyType != null && file != null && root.group(2) != null) {
                DtoDef dto = file.findDto(bodyType);
                FieldDef field = dto == null ? null : dto.getFields().stream()
                        .filter(candidate -> candidate.getName().equalsIgnoreCase(root.group(2))).findFirst().orElse(null);
                if (field != null && field.getType().isList()) elementType = field.getType().getElementTypeRef().getName();
            } else if (localVars.containsKey(owner) && !"body".equals(owner)) {
                String ownerType = localVars.get(owner);
                if (ownerType.startsWith("@loop:")) ownerType = ownerType.substring("@loop:".length());
                ModelDef ownerModel = file.findModel(ownerType);
                FieldDef field = ownerModel == null ? null : ownerModel.findField(root.group(2));
                if (field != null && field.getType().isList()) elementType = field.getType().getElementTypeRef().getName();
                if (elementType == null && ownerModel != null) {
                    RelationDef relation = ownerModel.findRelation(root.group(2));
                    if (relation != null && relation.getRelationType() == RelationType.ONE_TO_MANY) {
                        elementType = relation.getTargetModel();
                    }
                }
            } else if (root.group(2) != null && model != null
                    && (model.findField(root.group(1)) != null || model.findRelation(root.group(1)) != null)) {
                FieldDef field = model.findField(root.group(1));
                if (field != null && field.getType().isList()) elementType = field.getType().getElementTypeRef().getName();
                if (elementType == null) {
                    RelationDef relation = model.findRelation(root.group(1));
                    if (relation != null && relation.getRelationType() == RelationType.ONE_TO_MANY) {
                        elementType = relation.getTargetModel();
                    }
                }
            }
        }
        if (elementType == null && iterable.matches("(?s).*(?:[A-Za-z_][A-Za-z0-9_]*\\.)?[A-Za-z_][A-Za-z0-9_]*\\(\\).*")) {
            throw new AnalysisException("Cannot infer list element type for for loop iterable '" + iterable + "'", srcFile, srcLine, 1);
        }
        if (elementType == null) {
            throw new AnalysisException("For loop iterable '" + iterable + "' must be a known List<T> value", srcFile, srcLine, 1);
        }
        if (elementType.contains("<") || "List".equalsIgnoreCase(elementType)) {
            throw new AnalysisException("Nested list iteration is not supported; the for loop variable must have a scalar, DTO, enum, or model element type", srcFile, srcLine, 1);
        }
        if (!isScalarReturnType(elementType) && !modelNames.contains(elementType)
                && file.findDto(elementType) == null && file.findEnum(elementType) == null) {
            throw new AnalysisException("Unknown list element type '" + elementType + "' in for loop", srcFile, srcLine, 1);
        }
        validatePropertyAccesses(iterable, localVars, file, context, srcFile, srcLine);
        Map<String, String> bodyVars = new HashMap<>(localVars);
        bodyVars.put(variable, "@loop:" + elementType);
        validateActionBlockStatements(source.substring(open + 1, close), model, file, modelNames, context, srcFile, srcLine, bodyVars, bodyType);
    }

    private void validatePublishStatement(String source, ModelDef model, ZenithFile file, Map<String, String> localVars,
            String context, String srcFile, int srcLine) {
        if (model == null) throw new AnalysisException("Stream publication is currently supported only inside transactional model endpoint actions", srcFile, srcLine, 1);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^publish\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\((.*)\\)$", java.util.regex.Pattern.DOTALL).matcher(source);
        if (!matcher.matches()) throw new AnalysisException("Invalid publish statement; expected publish StreamName(field = expression, ...)", srcFile, srcLine, 1);
        StreamDef stream = file.getStreams().stream().filter(candidate -> candidate.getName().equalsIgnoreCase(matcher.group(1))).findFirst().orElse(null);
        if (stream == null) throw new AnalysisException("Publish references unknown stream '" + matcher.group(1) + "'", srcFile, srcLine, 1);
        EventDef event = file.findEvent(stream.getPayload());
        Map<String, String> supplied = new HashMap<>();
        for (String part : splitPublishArguments(matcher.group(2))) {
            int equals = part.indexOf('=');
            if (equals < 1) throw new AnalysisException("Publish arguments must use field = expression", srcFile, srcLine, 1);
            String fieldName = part.substring(0, equals).trim();
            String expression = part.substring(equals + 1).trim();
            if (expression.isEmpty()) throw new AnalysisException("Missing expression for event field '" + fieldName + "'", srcFile, srcLine, 1);
            FieldDef field = event.getFields().stream().filter(candidate -> candidate.getName().equalsIgnoreCase(fieldName)).findFirst().orElse(null);
            if (field == null) throw new AnalysisException("Event '" + event.getName() + "' has no field '" + fieldName + "'", srcFile, srcLine, 1);
            if (supplied.put(fieldName.toLowerCase(), expression) != null) throw new AnalysisException("Duplicate publish field '" + fieldName + "'", srcFile, srcLine, 1);
            validatePropertyAccesses(expression, localVars, file, context, srcFile, srcLine);
            validateExpressionFunctions(expression, context, srcFile, srcLine);
            String targetType = field.getType().getName().toLowerCase();
            if (expression.startsWith("\"") && !(targetType.equals("string") || targetType.equals("text")))
                throw new AnalysisException("String literal is incompatible with event field '" + fieldName + "' of type '" + field.getType().getName() + "'", srcFile, srcLine, 1);
            if (expression.matches("-?[0-9]+(\\.[0-9]+)?") && !Set.of("int", "integer", "long", "float", "double", "decimal", "bigdecimal").contains(targetType))
                throw new AnalysisException("Numeric literal is incompatible with event field '" + fieldName + "' of type '" + field.getType().getName() + "'", srcFile, srcLine, 1);
            if (("true".equalsIgnoreCase(expression) || "false".equalsIgnoreCase(expression)) && !(targetType.equals("boolean") || targetType.equals("bool")))
                throw new AnalysisException("Boolean literal is incompatible with event field '" + fieldName + "' of type '" + field.getType().getName() + "'", srcFile, srcLine, 1);
            if (model != null && isSimpleIdentifier(expression) && model.findField(expression) == null
                    && !localVars.containsKey(expression) && !Set.of("id", "body", "entity", "now").contains(expression)) {
                // Endpoint path variables and built-in values are resolved by the generated service signature.
                boolean isPathVariable = model.getCustomEndpoints().stream().anyMatch(ep -> ep.getPath().matches(".*[/{]" + java.util.regex.Pattern.quote(expression) + "[}].*"));
                if (!isPathVariable && !"null".equalsIgnoreCase(expression)) throw new AnalysisException("Cannot resolve publish expression '" + expression + "'", srcFile, srcLine, 1);
            }
        }
        for (FieldDef field : event.getFields()) {
            if (!field.isOptional() && !supplied.containsKey(field.getName().toLowerCase())) throw new AnalysisException("Publish to '" + stream.getName() + "' is missing required event field '" + field.getName() + "'", srcFile, srcLine, 1);
        }
    }

    private List<String> splitPublishArguments(String body) {
        List<String> parts = new ArrayList<>(); StringBuilder current = new StringBuilder();
        int depth = 0; boolean quoted = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"' && (i == 0 || body.charAt(i - 1) != '\\')) quoted = !quoted;
            if (!quoted) { if (c == '(') depth++; else if (c == ')') depth--; }
            if (c == ',' && !quoted && depth == 0) { if (!current.toString().trim().isEmpty()) parts.add(current.toString().trim()); current.setLength(0); }
            else current.append(c);
        }
        if (!current.toString().trim().isEmpty()) parts.add(current.toString().trim());
        return parts;
    }

    private boolean isSimpleIdentifier(String expression) { return expression.matches("[A-Za-z_][A-Za-z0-9_]*"); }

    private void validateTryCatchStatement(String source, ModelDef model, ZenithFile file, Set<String> modelNames,
            String context, String srcFile, int srcLine, Map<String, String> localVars) {
        int open = source.indexOf('{');
        int close = matchingActionBrace(source, open);
        if (close < 0) throw new AnalysisException("Unclosed try block", srcFile, srcLine, 1);
        validateActionBlockStatements(source.substring(open + 1, close), model, file, modelNames, context, srcFile, srcLine,
                new HashMap<>(localVars));
        int cursor = close + 1;
        int catches = 0;
        while (cursor < source.length()) {
            while (cursor < source.length() && (Character.isWhitespace(source.charAt(cursor)) || source.charAt(cursor) == ';')) cursor++;
            if (cursor == source.length()) break;
            if (!source.startsWith("catch", cursor)) throw new AnalysisException("Expected catch clause", srcFile, srcLine, 1);
            int parenOpen = source.indexOf('(', cursor + 5);
            int parenClose = parenOpen < 0 ? -1 : source.indexOf(')', parenOpen);
            if (parenClose < 0) throw new AnalysisException("Invalid catch clause", srcFile, srcLine, 1);
            String[] binding = source.substring(parenOpen + 1, parenClose).trim().split("\\s+");
            if (binding.length != 2) throw new AnalysisException("Catch requires an exception type and variable", srcFile, srcLine, 1);
            open = source.indexOf('{', parenClose);
            close = matchingActionBrace(source, open);
            if (open < 0 || close < 0) throw new AnalysisException("Invalid catch block", srcFile, srcLine, 1);
            Map<String, String> catchVars = new HashMap<>(localVars);
            if (catchVars.containsKey(binding[1]) || RESERVED_ACTION_VARS.contains(binding[1])) {
                throw new AnalysisException("Catch variable '" + binding[1] + "' shadows an existing action variable", srcFile, srcLine, 1);
            }
            catchVars.put(binding[1], "Exception");
            validateActionBlockStatements(source.substring(open + 1, close), model, file, modelNames, context, srcFile, srcLine, catchVars);
            cursor = close + 1;
            catches++;
        }
        if (catches == 0) throw new AnalysisException("A try block must have at least one catch clause", srcFile, srcLine, 1);
    }

    private void validateActionBlockStatements(String body, ModelDef model, ZenithFile file, Set<String> modelNames,
            String context, String srcFile, int srcLine, Map<String, String> variables) {
        validateActionBlockStatements(body, model, file, modelNames, context, srcFile, srcLine, variables, null);
    }

    private void validateActionBlockStatements(String body, ModelDef model, ZenithFile file, Set<String> modelNames,
            String context, String srcFile, int srcLine, Map<String, String> variables, String bodyType) {
        int start = 0, depth = 0;
        boolean quoted = false, escaped = false;
        for (int i = 0; i <= body.length(); i++) {
            char c = i == body.length() ? ';' : body.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{') depth++;
            else if (c == '}') depth--;
            if (c == ';' && depth == 0 && !quoted) {
                String statement = body.substring(start, i).trim();
                if (!statement.isEmpty()) validateActionStatement(statement, model, file, modelNames, context, srcFile, srcLine, variables, bodyType);
                start = i + 1;
            }
        }
    }

    private int matchingActionBrace(String source, int open) {
        if (open < 0) return -1;
        int depth = 0;
        boolean quoted = false, escaped = false;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i;
        }
        return -1;
    }

    private void validateExpressionFunctions(String expr, String context, String srcFile, int srcLine) {
        if (expr == null)
            return;
        java.util.regex.Pattern fnPattern = java.util.regex.Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(");
        java.util.regex.Matcher m = fnPattern.matcher(expr);
        Set<String> supportedFns = Set.of(
                "now", "today",
                "daysago", "daysfromnow",
                "hoursago", "hoursfromnow",
                "minutesago", "minutesfromnow",
                "monthsago", "monthsfromnow",
                "yearsago", "yearsfromnow",
                "notify");
        while (m.find()) {
            String fnName = m.group(1);
            if (fnName.startsWith("___STR_"))
                continue;
            if (fnName.equalsIgnoreCase("if") || fnName.equalsIgnoreCase("for") || fnName.equalsIgnoreCase("while"))
                continue;
            if (!supportedFns.contains(fnName.toLowerCase())) {
                throw new AnalysisException("Unsupported function '" + fnName + "(...)' in " + context
                        + " expression: '" + expr.trim() + "'. "
                        + "Supported date/time functions: now(), today(), daysAgo(n), daysFromNow(n), hoursAgo(n), hoursFromNow(n), minutesAgo(n), minutesFromNow(n). Supported operations: notify(recipient, message).",
                        srcFile, srcLine, 1);
            }
        }
    }

    private void validateEnumConstants(String targetField, String rightExpr, ModelDef model, ZenithFile file,
            String context, String srcFile, int srcLine, Map<String, String> localVars) {
        if (rightExpr == null || file == null || file.getEnums().isEmpty())
            return;

        FieldDef fDef = model != null ? model.findField(targetField) : null;
        String fieldTypeName = fDef != null ? fDef.getType().getName() : null;

        // Normalize whitespace around dots so "OrderStatus . PAIDs" becomes
        // "OrderStatus.PAIDs"
        String normalizedExpr = rightExpr.replaceAll("\\s*\\.\\s*", ".");

        // 1. Matches EnumName.CONSTANT_NAME (e.g. OrderStatus.PAIDd)
        java.util.regex.Pattern enumDotPattern = java.util.regex.Pattern
                .compile("\\b([A-Z][a-zA-Z0-9_]*)\\.([a-zA-Z0-9_]+)\\b");
        java.util.regex.Matcher m = enumDotPattern.matcher(normalizedExpr);
        while (m.find()) {
            String enumName = m.group(1);
            String constantName = m.group(2);

            EnumDef enumDef = null;
            for (EnumDef e : file.getEnums()) {
                if (e.getName().equalsIgnoreCase(enumName)) {
                    enumDef = e;
                    break;
                }
            }

            if (enumDef != null) {
                boolean validConstant = false;
                for (String val : enumDef.getValues()) {
                    if (val.equalsIgnoreCase(constantName)) {
                        validConstant = true;
                        break;
                    }
                }
                if (!validConstant) {
                    throw new AnalysisException(
                            "Unknown enum constant '" + constantName + "' for enum '" + enumDef.getName()
                                    + "' in " + context + ". Available values: "
                                    + String.join(", ", enumDef.getValues()),
                            srcFile, srcLine, 1);
                }
            }
        }

        // 2. Unqualified enum assignment e.g. status = PAIDd when status is an Enum
        if (fieldTypeName != null) {
            EnumDef targetEnum = null;
            for (EnumDef e : file.getEnums()) {
                if (e.getName().equalsIgnoreCase(fieldTypeName)) {
                    targetEnum = e;
                    break;
                }
            }
            if (targetEnum != null) {
                String token = normalizedExpr.trim();
                if (token.matches("^[a-zA-Z0-9_]+$")) {
                    // Check if token refers to a known local variable, parameter, model field, or reserved keyword
                    if (localVars != null && localVars.containsKey(token)) {
                        return;
                    }
                    if (RESERVED_ACTION_VARS.contains(token) || "null".equalsIgnoreCase(token)) {
                        return;
                    }
                    if (model != null && (model.findField(token) != null || model.findRelation(token) != null)) {
                        return;
                    }
                    if (context != null && context.contains("{" + token + "}")) {
                        return;
                    }

                    boolean validConstant = false;
                    for (String val : targetEnum.getValues()) {
                        if (val.equalsIgnoreCase(token)) {
                            validConstant = true;
                            break;
                        }
                    }
                    if (!validConstant) {
                        throw new AnalysisException("Invalid enum value '" + token + "' for enum field '" + targetField
                                + "' of type '" + targetEnum.getName() + "' in " + context + ". Available values: "
                                + String.join(", ", targetEnum.getValues()),
                                srcFile, srcLine, 1);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Spec #02: Database Indexes (@index / @uniqueIndex)
    // ---------------------------------------------------------------------

    private static final java.util.regex.Pattern SQL_IDENTIFIER = java.util.regex.Pattern
            .compile("^[A-Za-z_][A-Za-z0-9_]*$");

    /**
     * Validates every index declared on a model: referenced fields exist, types are
     * indexable,
     * no duplicated columns, no redundant duplicate indexes, and explicit names are
     * valid identifiers
     * within the 63-character database limit.
     */
    private void validateModelIndexes(ModelDef model, Set<String> enumNames) {
        Map<String, IndexDef> signatures = new HashMap<>();
        FieldDef idField = model.getIdField();

        for (IndexDef idx : model.getEffectiveIndexes()) {
            String srcFile = idx.getSourceFile() != null ? idx.getSourceFile() : model.getSourceFile();
            int srcLine = idx.getSourceLine() > 0 ? idx.getSourceLine() : model.getSourceLine();
            String label = idx.isInline()
                    ? (idx.isUnique() ? "@uniqueIndex" : "@index") + " on field '" + idx.getFields().get(0) + "'"
                    : (idx.isUnique() ? "@uniqueIndex" : "@index") + idx.getFields();

            if (idx.getFields().isEmpty()) {
                throw new AnalysisException(
                        "Index " + label + " in model '" + model.getName() + "' must reference at least one field",
                        srcFile, srcLine, 1);
            }

            Set<String> seenCols = new HashSet<>();
            List<String> normalized = new ArrayList<>();
            for (String fname : idx.getFields()) {
                FieldDef f = model.findField(fname);
                RelationDef rel = null;
                if (f == null) {
                    for (RelationDef r : model.getRelations()) {
                        if (r.getFieldName().equalsIgnoreCase(fname)) {
                            rel = r;
                            break;
                        }
                    }
                }
                if (f == null && rel == null) {
                    List<String> available = new ArrayList<>();
                    for (FieldDef af : model.getFields())
                        available.add(af.getName());
                    for (RelationDef r : model.getRelations()) {
                        if (r.getRelationType() == RelationType.MANY_TO_ONE
                                || r.getRelationType() == RelationType.ONE_TO_ONE) {
                            available.add(r.getFieldName());
                        }
                    }
                    String suggestion = closestMatch(fname, available);
                    throw new AnalysisException("Index " + label + " in model '" + model.getName()
                            + "' references unknown field '" + fname + "'."
                            + (suggestion != null ? " Did you mean '" + suggestion + "'?" : "")
                            + " Available fields: " + available,
                            srcFile, srcLine, 1);
                }
                if (rel != null && rel.getRelationType() != RelationType.MANY_TO_ONE
                        && rel.getRelationType() != RelationType.ONE_TO_ONE) {
                    throw new AnalysisException("Index " + label + " in model '" + model.getName()
                            + "' cannot index collection relation '" + rel.getFieldName() + "' ("
                            + rel.getRelationType()
                            + "). Only manyToOne / oneToOne relations (foreign key columns) are indexable.",
                            srcFile, srcLine, 1);
                }
                if (f != null) {
                    String t = f.getType().getName();
                    if (f.getType().isList() || f.hasAnnotation("text") || "text".equalsIgnoreCase(t)
                            || "bytes".equalsIgnoreCase(t) || "bytearray".equalsIgnoreCase(t)) {
                        throw new AnalysisException("Index " + label + " in model '" + model.getName()
                                + "' cannot index field '" + f.getName() + "' of unindexable type '"
                                + (f.hasAnnotation("text") ? "Text" : t)
                                + "'. Large text, binary and list columns cannot be B-tree indexed.",
                                srcFile, srcLine, 1);
                    }
                }
                String canonical = f != null ? f.getName() : rel.getFieldName();
                if (!seenCols.add(canonical.toLowerCase())) {
                    throw new AnalysisException("Index " + label + " in model '" + model.getName()
                            + "' lists field '" + canonical + "' more than once", srcFile, srcLine, 1);
                }
                normalized.add(canonical.toLowerCase());
            }

            // Indexes on the primary key are silently normalized (PK is already indexed)
            if (normalized.size() == 1 && idField != null && idField.getName().equalsIgnoreCase(normalized.get(0))) {
                continue;
            }

            String sig = String.join(",", normalized);
            IndexDef previous = signatures.put(sig, idx);
            if (previous != null) {
                throw new AnalysisException("Redundant duplicate index in model '" + model.getName() + "': " + label
                        + " covers the same columns " + idx.getFields()
                        + " as an earlier index declaration. Remove one of them.",
                        srcFile, srcLine, 1);
            }

            if (idx.hasExplicitName()) {
                String n = idx.getName();
                if (!SQL_IDENTIFIER.matcher(n).matches()) {
                    throw new AnalysisException("Index name '" + n + "' in model '" + model.getName()
                            + "' is not a valid SQL identifier (letters, digits and '_' only; must not start with a digit)",
                            srcFile, srcLine, 1);
                }
                if (n.length() > dev.zenith.generator.IndexResolver.MAX_IDENTIFIER_LENGTH) {
                    throw new AnalysisException(
                            "Index name '" + n + "' in model '" + model.getName() + "' is " + n.length()
                                    + " characters long; database identifiers are limited to "
                                    + dev.zenith.generator.IndexResolver.MAX_IDENTIFIER_LENGTH + " characters",
                            srcFile, srcLine, 1);
                }
            }
        }
    }

    /**
     * Index names are schema-global in PostgreSQL, so they must be unique across
     * all models.
     */
    private void validateIndexNameCollisions(List<ModelDef> models) {
        Map<String, String> owners = new HashMap<>();
        for (ModelDef m : models) {
            for (dev.zenith.generator.IndexResolver.ResolvedIndex idx : dev.zenith.generator.IndexResolver.resolve(m)) {
                String key = idx.name.toLowerCase();
                String owner = owners.put(key, m.getName());
                if (owner != null) {
                    IndexDef src = idx.source;
                    throw new AnalysisException(
                            "Index name '" + idx.name + "' is declared more than once (models '" + owner
                                    + "' and '" + m.getName() + "'). Index names must be unique across the schema.",
                            src.getSourceFile() != null ? src.getSourceFile() : m.getSourceFile(),
                            src.getSourceLine() > 0 ? src.getSourceLine() : m.getSourceLine(), 1);
                }
            }
        }
    }

    private static String closestMatch(String target, List<String> candidates) {
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (String c : candidates) {
            int d = levenshtein(target.toLowerCase(), c.toLowerCase());
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return (best != null && bestDist <= Math.max(2, target.length() / 3)) ? best : null;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++)
            prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()];
    }

    private FieldDef findFieldCaseInsensitive(List<FieldDef> fields, String name) {
        for (FieldDef f : fields) {
            if (f.getName().equalsIgnoreCase(name)) {
                return f;
            }
        }
        return null;
    }

    private void validatePropertyAccesses(String expr, Map<String, String> localVars, ZenithFile file, String context,
            String srcFile, int srcLine) {
        if (expr == null || localVars == null || file == null)
            return;
        // Strip string literals before analyzing dots
        String stripped = expr.replaceAll("\"(\\\\.|[^\"])*\"", "\"\"");
        stripped = stripped.replaceAll("\\s*\\.\\s*", ".");
        java.util.regex.Pattern dotPattern = java.util.regex.Pattern
                .compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\.([a-zA-Z0-9_.]+)\\b");
        java.util.regex.Matcher m = dotPattern.matcher(stripped);
        while (m.find()) {
            String root = m.group(1);
            if (root.equals("entity") || root.equals("currentUser") || root.equals("java")
                    || root.equals("Math") || root.equals("ShipmentStatus")) {
                continue;
            }
            if (root.equals("body")) {
                String bodyType = localVars.get("body");
                DtoDef bodyDto = file.findDto(bodyType);
                if (bodyDto != null) {
                    String firstProperty = m.group(2).split("\\.")[0];
                    if (bodyDto.getFields().stream().noneMatch(field -> field.getName().equalsIgnoreCase(firstProperty))) {
                        throw new AnalysisException("Property '" + firstProperty + "' does not exist on request DTO '" + bodyDto.getName() + "' in " + context, srcFile, srcLine, 1);
                    }
                }
                continue;
            }
            if (file.findEnum(root) != null) {
                continue;
            }
            // Check if root is in localVars
            if (localVars.containsKey(root)) {
                String targetModelName = localVars.get(root);
                if (targetModelName.startsWith("@loop:")) targetModelName = targetModelName.substring("@loop:".length());
                DtoDef targetDto = file.findDto(targetModelName);
                if (targetDto != null) {
                    String[] segments = m.group(2).split("\\.");
                    if (targetDto.getFields().stream().noneMatch(field -> field.getName().equalsIgnoreCase(segments[0]))) {
                        throw new AnalysisException("Property '" + segments[0] + "' does not exist on DTO '" + targetDto.getName() + "' in " + context, srcFile, srcLine, 1);
                    }
                    continue;
                }
                ModelDef targetModel = file.findModel(targetModelName);
                if (targetModel != null) {
                    String fullProp = m.group(2);
                    String[] segments = fullProp.split("\\.");
                    ModelDef currentModel = targetModel;
                    for (int i = 0; i < segments.length; i++) {
                        String seg = segments[i];
                        if (currentModel == null) {
                            throw new AnalysisException(
                                    "Cannot navigate property '" + seg + "' on non-model in " + context, srcFile,
                                    srcLine, 1);
                        }
                        if (seg.equalsIgnoreCase("id") || seg.equalsIgnoreCase("createdAt")
                                || seg.equalsIgnoreCase("updatedAt")) {
                            currentModel = null;
                            continue;
                        }
                        var f = currentModel.findField(seg);
                        if (f != null) {
                            currentModel = null;
                            continue;
                        }
                        var r = currentModel.findRelation(seg);
                        if (r != null) {
                            currentModel = file.findModel(r.getTargetModel());
                            continue;
                        }
                        throw new AnalysisException("Property '" + seg + "' does not exist on model '"
                                + currentModel.getName() + "' in " + context, srcFile, srcLine, 1);
                    }
                } else if (!"Exception".equals(targetModelName) || !"message".equals(m.group(2))) {
                    throw new AnalysisException("Cannot access property '" + m.group(2) + "' on value of type '"
                            + targetModelName + "' in " + context, srcFile, srcLine, 1);
                }
            } else if (!root.matches("^[A-Z].*")) {
                // Undeclared variable reference
                throw new AnalysisException(
                        "Undefined variable '" + root + "' referenced before declaration in " + context, srcFile,
                        srcLine, 1);
            }
        }
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
        if (target == null)
            return null;
        for (String s : set) {
            if (s.equalsIgnoreCase(target)) {
                return s;
            }
        }
        return null;
    }

    private boolean isAutoGeneratedModelDto(String typeName, Set<String> modelNames) {
        if (typeName == null)
            return false;
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

    /**
     * @param file
     * @param modelNames
     */
    private void validateMailDefinitions(ZenithFile file, Set<String> modelNames) {
        Set<String> mailNames = new HashSet<>();
        for (MailDef mail : file.getMails()) {
            if (!mailNames.add(mail.getName().toLowerCase())) {
                throw new AnalysisException("Duplicate mail definition '" + mail.getName() + "'",
                        mail.getSourceFile(), mail.getSourceLine(), 1);
            }

            if (mail.getParameters().isEmpty()) {
                throw new AnalysisException(
                        "Mail contract '" + mail.getName() + "' must declare at least one parameter.",
                        mail.getSourceFile(), mail.getSourceLine(), 1);
            }

            // Check duplicate parameters & valid types
            Set<String> paramNames = new HashSet<>();
            for (FieldDef param : mail.getParameters()) {
                if (!paramNames.add(param.getName().toLowerCase())) {
                    throw new AnalysisException(
                            "Duplicate parameter '" + param.getName() + "' in mail '" + mail.getName() + "'",
                            mail.getSourceFile(), mail.getSourceLine(), 1);
                }
                String tName = param.getType().getName();
                boolean isKnown = isScalarReturnType(tName)
                        || containsIgnoreCase(modelNames, tName)
                        || containsIgnoreCase(file.getAllDefinedRoles(), tName)
                        || file.findEnum(tName) != null
                        || file.findDto(tName) != null;
                if (!isKnown) {
                    throw new AnalysisException("Unknown type '" + tName + "' for parameter '" + param.getName()
                            + "' in mail '" + mail.getName() + "'", mail.getSourceFile(), mail.getSourceLine(), 1);
                }
            }

            // Validate 'to' expression
            if (mail.getToExpression() == null || mail.getToExpression().trim().isEmpty()) {
                throw new AnalysisException(
                        "Mail contract '" + mail.getName() + "' must define a non-empty recipient 'to:' expression.",
                        mail.getSourceFile(), mail.getSourceLine(), 1);
            }

            // Validate 'subject' expression
            if (mail.getSubjectExpression() == null || mail.getSubjectExpression().trim().isEmpty()) {
                throw new AnalysisException(
                        "Mail contract '" + mail.getName() + "' must define a non-empty 'subject:' expression.",
                        mail.getSourceFile(), mail.getSourceLine(), 1);
            }

            // Validate that either 'content' or 'template' is specified
            if (!mail.hasContent() && !mail.hasTemplate()) {
                throw new AnalysisException(
                        "Mail contract '" + mail.getName()
                                + "' must define either 'content:' (inline expression) or 'template:' (file path).",
                        mail.getSourceFile(), mail.getSourceLine(), 1);
            }
        }
    }

    private void validateSendActionStatement(String stmt, ZenithFile file, String context, String srcFile,
            int srcLine) {
        String s = stmt.trim();
        // format: send MailName(param1 = value1, param2 = value2)
        int parenOpen = s.indexOf('(');
        int parenClose = s.lastIndexOf(')');
        if (parenOpen < 0 || parenClose < parenOpen) {
            throw new AnalysisException(
                    "Malformed 'send' statement in " + context + ": expected 'send MailName(arg = val, ...)'", srcFile,
                    srcLine, 1);
        }
        String mailName = s.substring(4, parenOpen).trim();
        MailDef mailDef = file.findMail(mailName);
        if (mailDef == null) {
            throw new AnalysisException("Referenced mail contract '" + mailName + "' does not exist in " + context,
                    srcFile, srcLine, 1);
        }

        String argsBlock = s.substring(parenOpen + 1, parenClose).trim();
        Map<String, String> passedArgs = new HashMap<>();
        if (!argsBlock.isEmpty()) {
            String[] parts = argsBlock.split(",");
            for (String part : parts) {
                String p = part.trim();
                if (p.isEmpty())
                    continue;
                String[] kv = p.split("=", 2);
                if (kv.length == 2) {
                    passedArgs.put(kv[0].trim().toLowerCase(), kv[1].trim());
                } else {
                    // Positional or shorthand: use name as key and value
                    passedArgs.put(kv[0].trim().toLowerCase(), kv[0].trim());
                }
            }
        }

        // Verify that all required parameters of the target MailDef are provided
        for (FieldDef param : mailDef.getParameters()) {
            if (!passedArgs.containsKey(param.getName().toLowerCase())) {
                throw new AnalysisException("Missing required argument '" + param.getName() + "' in 'send "
                        + mailName + "(...)' call in " + context, srcFile, srcLine, 1);
            }
        }
    }

    private boolean isScalarReturnType(String typeName) {
        if (typeName == null)
            return false;
        Set<String> scalars = Set.of(
                "string", "int", "integer", "long", "boolean", "bool",
                "double", "float", "decimal", "bigdecimal", "uuid",
                "datetime", "date", "void");
        return scalars.contains(typeName.toLowerCase());
    }
}
