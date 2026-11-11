package dev.zenith.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GuardDef {
    private final String expression;
    private final String errorMessage;

    public GuardDef(String expression, String errorMessage) {
        this.expression = expression != null ? expression.replaceAll("\\s*\\.\\s*", ".").trim() : "";
        this.errorMessage = errorMessage != null ? errorMessage.trim() : "Condition not met";
    }

    public String getExpression() {
        return expression;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean hasRoleCondition() {
        return !extractRoles().isEmpty();
    }

    public boolean isRoleOnlyGuard() {
        return hasRoleCondition() && getEntityCondition().isEmpty();
    }

    public boolean isRoleGuard() {
        return hasRoleCondition();
    }

    public List<String> extractRoles() {
        List<String> roles = new ArrayList<>();
        // Matches `role == Role.XYZ` or `role == 'XYZ'` or `role == XYZ`
        Pattern pEqual = Pattern.compile("(?i)\\brole\\s*==\\s*([a-zA-Z0-9_.\'\"]+)");
        Matcher mEqual = pEqual.matcher(expression);
        while (mEqual.find()) {
            String r = cleanRoleToken(mEqual.group(1));
            if (!r.isEmpty() && !roles.contains(r)) {
                roles.add(r);
            }
        }

        // Matches `role in [ADMIN, MANAGER]`
        Pattern pIn = Pattern.compile("(?i)\\brole\\s+in\\s*\\[([^\\]]+)\\]");
        Matcher mIn = pIn.matcher(expression);
        while (mIn.find()) {
            String listStr = mIn.group(1);
            for (String item : listStr.split(",")) {
                String r = cleanRoleToken(item.trim());
                if (!r.isEmpty() && !roles.contains(r)) {
                    roles.add(r);
                }
            }
        }

        return roles;
    }

    public String extractRole() {
        List<String> roles = extractRoles();
        return roles.isEmpty() ? null : roles.get(0);
    }

    private static String cleanRoleToken(String token) {
        if (token == null) return "";
        token = token.trim();
        if (token.contains(".")) {
            token = token.substring(token.lastIndexOf('.') + 1);
        }
        return token.replace("\"", "").replace("'", "").trim();
    }

    /**
     * Extracts the entity-specific condition by stripping out role checks.
     * E.g. "role == UserRole.MANAGER and status == TaskStatus.IN_PROGRESS" -> "status == TaskStatus.IN_PROGRESS"
     * E.g. "role == UserRole.MANAGER" -> ""
     */
    public String getEntityCondition() {
        String cond = expression;
        cond = cond.replaceAll("(?i)\\brole\\s+in\\s*\\[[^\\]]+\\]", "");
        cond = cond.replaceAll("(?i)\\brole\\s*==\\s*([a-zA-Z0-9_.]+|\"[^\"]*\"|'[^']*')", "");

        String prev;
        do {
            prev = cond;
            cond = cond.replaceAll("(?i)^\\s*(&&|\\|\\||and|or)\\s*", "");
            cond = cond.replaceAll("(?i)\\s*(&&|\\|\\||and|or)\\s*$", "");
            cond = cond.replaceAll("(?i)\\s*(&&|\\|\\||and|or)\\s*(&&|\\|\\||and|or)\\s*", " $1 ");
            cond = cond.replaceAll("\\(\\s*\\)", "");
        } while (!cond.equals(prev));

        return cond.trim();
    }
}
