package dev.zenith.engine;

import dev.zenith.ast.CustomEndpointDef;
import dev.zenith.ast.GuardDef;
import dev.zenith.ast.ModelDef;
import dev.zenith.ast.ZenithFile;
import dev.zenith.engine.trace.DispatchedEffect;
import dev.zenith.engine.trace.ExecutionTrace;
import dev.zenith.engine.trace.StateDiff;
import dev.zenith.engine.value.*;

import java.math.BigDecimal;
import java.util.*;

public class ZenithEngine {
    private static final ZenithEngine INSTANCE = new ZenithEngine();

    public static ZenithEngine getInstance() {
        return INSTANCE;
    }

    public SimulationResult simulate(ZenithFile schema, String modelName, String endpointPath, String method,
                                     Map<String, Object> mockEntityData, Map<String, Object> mockBodyData,
                                     Map<String, Object> mockCurrentUser) {
        long startTime = System.currentTimeMillis();
        ExecutionTrace trace = new ExecutionTrace();
        List<DispatchedEffect> effects = new ArrayList<>();
        Map<String, StateDiff> diffMap = new LinkedHashMap<>();

        // Locate target model and endpoint
        ModelDef targetModel = null;
        CustomEndpointDef targetEndpoint = null;

        if (schema != null && schema.getModels() != null) {
            for (ModelDef m : schema.getModels()) {
                if (modelName != null && modelName.equalsIgnoreCase(m.getName())) {
                    targetModel = m;
                    break;
                }
            }
        }

        if (targetModel != null && targetModel.getCustomEndpoints() != null) {
            String normMethod = method != null ? method.trim().toUpperCase() : "POST";
            String normPath = endpointPath != null ? endpointPath.trim() : "";

            for (CustomEndpointDef ep : targetModel.getCustomEndpoints()) {
                if (ep.getMethod().equalsIgnoreCase(normMethod)) {
                    // Match path or simplified path
                    String p1 = ep.getPath().replaceAll("^/+", "").replaceAll("/+$", "");
                    String p2 = normPath.replaceAll("^/+", "").replaceAll("/+$", "");
                    if (p1.equalsIgnoreCase(p2) || matchPathPatterns(ep.getPath(), normPath)) {
                        targetEndpoint = ep;
                        break;
                    }
                }
            }
        }

        if (targetEndpoint == null) {
            long duration = Math.max(1, System.currentTimeMillis() - startTime);
            trace.addStep("❌ Endpoint not found: " + method + " " + endpointPath + " on model " + modelName);
            return new SimulationResult(false, 404, "Endpoint not found in schema", duration,
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), effects, trace.getSteps());
        }

        // Build runtime environment (Scope)
        Scope globalScope = new Scope();

        // 1. Convert mock entity data into ZenithEntityValue
        Map<String, ZenithValue> initialEntityProps = new LinkedHashMap<>();
        if (mockEntityData != null) {
            for (Map.Entry<String, Object> e : mockEntityData.entrySet()) {
                initialEntityProps.put(e.getKey(), wrapJavaValue(e.getValue()));
            }
        }
        ZenithEntityValue entity = new ZenithEntityValue(targetModel.getName(), initialEntityProps);
        globalScope.define("entity", entity);

        // Also define each entity field directly in scope for shorthand access (e.g. `status`)
        for (Map.Entry<String, ZenithValue> e : initialEntityProps.entrySet()) {
            globalScope.define(e.getKey(), e.getValue());
        }

        // 2. Bind request body if provided
        Map<String, ZenithValue> bodyProps = new LinkedHashMap<>();
        if (mockBodyData != null) {
            for (Map.Entry<String, Object> e : mockBodyData.entrySet()) {
                bodyProps.put(e.getKey(), wrapJavaValue(e.getValue()));
            }
        }
        ZenithEntityValue body = new ZenithEntityValue("RequestBody", bodyProps);
        globalScope.define("body", body);
        globalScope.define("dto", body);
        for (Map.Entry<String, ZenithValue> e : bodyProps.entrySet()) {
            if (!globalScope.contains(e.getKey())) {
                globalScope.define(e.getKey(), e.getValue());
            }
        }

        // 3. Bind user if provided
        Map<String, ZenithValue> userProps = new LinkedHashMap<>();
        if (mockCurrentUser != null) {
            for (Map.Entry<String, Object> e : mockCurrentUser.entrySet()) {
                userProps.put(e.getKey(), wrapJavaValue(e.getValue()));
            }
        }
        ZenithEntityValue user = new ZenithEntityValue("User", userProps);
        globalScope.define("currentUser", user);
        globalScope.define("user", user);

        // Check authentication if endpoint requires it
        if (Boolean.TRUE.equals(targetEndpoint.getRequiresAuth()) && (mockCurrentUser == null || mockCurrentUser.isEmpty())) {
            long duration = Math.max(1, System.currentTimeMillis() - startTime);
            trace.addStep("❌ Simulation rejected: Endpoint requires authentication, but no current user was supplied.");
            return new SimulationResult(false, 401, "Unauthorized: Authentication required", duration,
                    Collections.emptyMap(), Collections.emptyMap(), entity.getProperties().isEmpty() ? Collections.emptyMap() : (Map<String, Object>) entity.getRawValue(),
                    effects, trace.getSteps());
        }

        // Execute Preconditions / Guards
        Interpreter interpreter = new Interpreter();
        trace.addStep("▶ Starting simulation for " + targetEndpoint.getMethod() + " " + targetEndpoint.getPath());

        for (GuardDef guard : targetEndpoint.getGuards()) {
            boolean passed = interpreter.executeGuards(Collections.singletonList(guard), globalScope, trace);
            if (!passed) {
                long duration = Math.max(1, System.currentTimeMillis() - startTime);
                return new SimulationResult(false, 400, "Guard condition failed: " + guard.getErrorMessage(),
                        duration, Collections.emptyMap(), globalScope.getAllRawBindings(),
                        (Map<String, Object>) entity.getRawValue(), effects, trace.getSteps());
            }
        }

        // Execute Action Statements
        try {
            for (String stmt : targetEndpoint.getActionStatements()) {
                interpreter.executeStatement(stmt, globalScope, entity, effects, trace);
            }
        } catch (Exception ex) {
            long duration = Math.max(1, System.currentTimeMillis() - startTime);
            trace.addStep("💥 Execution error: " + ex.getMessage());
            return new SimulationResult(false, 500, "Simulation error: " + ex.getMessage(), duration,
                    Collections.emptyMap(), globalScope.getAllRawBindings(),
                    (Map<String, Object>) entity.getRawValue(), effects, trace.getSteps());
        }

        // Compute State Diffs
        for (Map.Entry<String, ZenithValue> mutated : entity.getMutatedProperties().entrySet()) {
            String field = mutated.getKey();
            ZenithValue before = entity.getInitialProperties().getOrDefault(field, ZenithNullValue.INSTANCE);
            ZenithValue after = mutated.getValue();
            diffMap.put(field, new StateDiff(field, before.getRawValue(), after.getRawValue()));
        }

        long duration = Math.max(1, System.currentTimeMillis() - startTime);
        trace.addStep("✔ Simulation completed in " + duration + "ms with HTTP 200 OK");

        Map<String, Object> boundVars = new LinkedHashMap<>();
        for (Map.Entry<String, ZenithValue> entry : globalScope.getLocalBindings().entrySet()) {
            String k = entry.getKey();
            if (!k.equals("entity") && !k.equals("body") && !k.equals("dto") && !k.equals("user") && !k.equals("currentUser")) {
                boundVars.put(k, entry.getValue().getRawValue());
            }
        }

        return new SimulationResult(true, 200, "Simulation executed successfully", duration,
                diffMap, boundVars, (Map<String, Object>) entity.getRawValue(), effects, trace.getSteps());
    }

    private boolean matchPathPatterns(String pattern, String path) {
        if (pattern == null || path == null) return false;
        String regex = pattern.replaceAll("\\{[^/]+\\}", "[^/]+");
        return path.matches(regex);
    }

    public static ZenithValue wrapJavaValue(Object val) {
        if (val == null) return ZenithNullValue.INSTANCE;
        if (val instanceof ZenithValue) return (ZenithValue) val;
        if (val instanceof String) return new ZenithStringValue((String) val);
        if (val instanceof Boolean) return ZenithBooleanValue.of((Boolean) val);
        if (val instanceof BigDecimal) return new ZenithNumberValue((BigDecimal) val);
        if (val instanceof Number) return new ZenithNumberValue(new BigDecimal(val.toString()));
        if (val instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) val;
            Map<String, ZenithValue> entityProps = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                entityProps.put(String.valueOf(entry.getKey()), wrapJavaValue(entry.getValue()));
            }
            return new ZenithEntityValue("Object", entityProps);
        }
        if (val instanceof List) {
            List<?> list = (List<?>) val;
            ZenithListValue listVal = new ZenithListValue();
            for (Object item : list) {
                listVal.add(wrapJavaValue(item));
            }
            return listVal;
        }
        return new ZenithStringValue(val.toString());
    }
}
