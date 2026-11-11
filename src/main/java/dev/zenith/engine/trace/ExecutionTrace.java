package dev.zenith.engine.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExecutionTrace {
    private final List<String> steps;

    public ExecutionTrace() {
        this.steps = new ArrayList<>();
    }

    public void addStep(String step) {
        this.steps.add(step);
    }

    public List<String> getSteps() {
        return Collections.unmodifiableList(steps);
    }
}

