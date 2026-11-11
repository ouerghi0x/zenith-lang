package dev.zenith.engine;

import dev.zenith.engine.trace.DispatchedEffect;
import dev.zenith.engine.trace.StateDiff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SimulationResult {
    private final boolean success;
    private final int status;
    private final String message;
    private final long executionTimeMs;
    private final Map<String, StateDiff> stateDiff;
    private final Map<String, Object> boundVariables;
    private final Map<String, Object> finalEntityState;
    private final List<DispatchedEffect> dispatchedEffects;
    private final List<String> trace;

    public SimulationResult(boolean success, int status, String message, long executionTimeMs,
                            Map<String, StateDiff> stateDiff, Map<String, Object> boundVariables,
                            Map<String, Object> finalEntityState,
                            List<DispatchedEffect> dispatchedEffects, List<String> trace) {
        this.success = success;
        this.status = status;
        this.message = message;
        this.executionTimeMs = executionTimeMs;
        this.stateDiff = stateDiff != null ? stateDiff : new LinkedHashMap<>();
        this.boundVariables = boundVariables != null ? boundVariables : new LinkedHashMap<>();
        this.finalEntityState = finalEntityState != null ? finalEntityState : new LinkedHashMap<>();
        this.dispatchedEffects = dispatchedEffects != null ? dispatchedEffects : new ArrayList<>();
        this.trace = trace != null ? trace : new ArrayList<>();
    }

    public boolean isSuccess() {
        return success;
    }

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public Map<String, StateDiff> getStateDiff() {
        return Collections.unmodifiableMap(stateDiff);
    }

    public Map<String, Object> getBoundVariables() {
        return Collections.unmodifiableMap(boundVariables);
    }

    public Map<String, Object> getFinalEntityState() {
        return Collections.unmodifiableMap(finalEntityState);
    }

    public List<DispatchedEffect> getDispatchedEffects() {
        return Collections.unmodifiableList(dispatchedEffects);
    }

    public List<String> getTrace() {
        return Collections.unmodifiableList(trace);
    }
}

