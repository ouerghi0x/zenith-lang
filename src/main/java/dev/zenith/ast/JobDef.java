package dev.zenith.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JobDef {
    private final String name;
    private final String cronExpression;
    private final String fixedRate;
    private final List<GuardDef> guards;
    private final List<String> actionStatements;
    private final String description;
    private final boolean enabled;

    public JobDef(String name, String cronExpression, String fixedRate, List<GuardDef> guards,
            List<String> actionStatements, String description, boolean enabled) {
        this.name = name;
        this.cronExpression = cronExpression != null ? cronExpression.trim() : "";
        this.fixedRate = fixedRate != null ? fixedRate.trim() : "";
        this.guards = guards != null ? guards : new ArrayList<>();
        this.actionStatements = actionStatements != null ? actionStatements : new ArrayList<>();
        this.description = description != null ? description.trim() : "";
        this.enabled = enabled;
    }

    public JobDef(String name, String cronExpression, List<GuardDef> guards, List<String> actionStatements,
            String description) {
        this(name, cronExpression, "", guards, actionStatements, description, true);
    }

    public String getName() {
        return name;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public String getFixedRate() {
        return fixedRate;
    }

    public boolean hasCron() {
        return cronExpression != null && !cronExpression.isEmpty();
    }

    public boolean hasFixedRate() {
        return fixedRate != null && !fixedRate.isEmpty();
    }

    public List<GuardDef> getGuards() {
        return Collections.unmodifiableList(guards);
    }

    public List<String> getActionStatements() {
        return Collections.unmodifiableList(actionStatements);
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
