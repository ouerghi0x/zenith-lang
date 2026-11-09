package dev.zenith.mcp;

import java.util.*;

/**
 * Intelligent error explainer providing human and AI friendly guidance,
 * error code classification (ZEN-xxx), and actionable suggestions.
 */
public class ErrorExplainer {

    public static class Explanation {
        public final String code;
        public final String category;
        public final String explanation;
        public final String suggestion;
        public final String example;

        public Explanation(String code, String category, String explanation, String suggestion, String example) {
            this.code = code;
            this.category = category;
            this.explanation = explanation;
            this.suggestion = suggestion;
            this.example = example;
        }

        public JsonValue toJson() {
            JsonValue obj = JsonValue.ofObject();
            obj.put("code", code);
            obj.put("category", category);
            obj.put("explanation", explanation);
            if (suggestion != null) obj.put("suggestion", suggestion);
            if (example != null) obj.put("example", example);
            return obj;
        }
    }

    public static Explanation explain(String errorCode, String errorMessage) {
        String msg = (errorMessage != null ? errorMessage : "").toLowerCase();

        // 1. Roles public anti-pattern
        if (msg.contains("roles: public") || msg.contains("invalid role 'public'")) {
            return new Explanation("ZEN-101", "Authorization Anti-Pattern",
                    "Zenith does not support 'roles: public'. In Zenith, public access is declared explicitly using 'auth: false'.",
                    "Replace 'roles: [public]' or 'roles: public' with 'auth: false'.",
                    "get \"/public-endpoint\" -> Result {\n    auth: false\n    action: { ... }\n}");
        }

        // 2. Roles authenticated anti-pattern
        if (msg.contains("roles: authenticated") || msg.contains("invalid role 'authenticated'")) {
            return new Explanation("ZEN-102", "Authorization Anti-Pattern",
                    "Zenith does not support 'roles: authenticated'. Any authenticated user is granted access using 'auth: true'.",
                    "Replace 'roles: [authenticated]' with 'auth: true'.",
                    "get \"/user-profile\" -> UserDto {\n    auth: true\n    action: { ... }\n}");
        }

        // 3. Unknown role reference
        if (msg.contains("is not declared in enum") || msg.contains("undefined role") || msg.contains("cannot find role")) {
            return new Explanation("ZEN-103", "Undefined Security Role",
                    "An endpoint references a security role that is not defined in any enum decorated with @role.",
                    "Define the role in an enum with @role (e.g. enum Role { ADMIN @role, USER @role }).",
                    "enum Role {\n    ADMIN @role\n    CUSTOMER @role\n}");
        }

        // 4. Missing primary key @id
        if (msg.contains("must define an @id field") || msg.contains("no primary key") || msg.contains("missing @id")) {
            return new Explanation("ZEN-201", "Missing Primary Key",
                    "Every model in Zenith must declare a primary key field annotated with '@id' (typically with '@auto').",
                    "Add an 'id: Long @id @auto' or 'id: UUID @id @auto' field to the model.",
                    "model Product {\n    id: Long @id @auto\n    name: String\n}");
        }

        // 5. Unknown annotation or typo
        if (msg.contains("unknown annotation") || msg.contains("@autoo") || msg.contains("invalid annotation")) {
            String typoGuess = "@auto";
            if (msg.contains("@autoo")) typoGuess = "@auto";
            else if (msg.contains("@idx")) typoGuess = "@index";
            return new Explanation("ZEN-202", "Invalid Annotation",
                    "The specified annotation is not recognized by the Zenith compiler.",
                    "Check spelling against supported annotations: @id, @auto, @unique, @index, @email, @password, @role, @rateLimit, @file, @image.",
                    "id: Long @id @auto");
        }

        // 6. Action variable duplicate or shadowing
        if (msg.contains("already declared in this action") || msg.contains("duplicate variable")) {
            return new Explanation("ZEN-301", "Duplicate Action Variable",
                    "A variable with this identifier has already been declared within the same action block.",
                    "Rename the variable to a unique identifier within the action block.",
                    "action: {\n    var first = create Order { ... }\n    var second = create Order { ... }\n}");
        }

        // 7. Reserved action identifier
        if (msg.contains("reserved keyword") || msg.contains("cannot be used as a variable name in an action")) {
            return new Explanation("ZEN-302", "Reserved Action Variable Name",
                    "Action identifiers like 'entity', 'currentUser', 'body', 'id' are reserved in action scopes.",
                    "Choose an alternative descriptive name (e.g. 'createdOrder', 'auditLog').",
                    "action: {\n    var auditEntry = create AuditLog { ... }\n}");
        }

        // 8. Syntax error / unexpected token
        if (msg.contains("syntax error") || msg.contains("expected") || msg.contains("unexpected token")) {
            return new Explanation("ZEN-401", "Syntax Error",
                    "The Zenith parser encountered an unexpected character or missing token.",
                    "Check for missing braces, missing colons, or unclosed string literals.",
                    "model User {\n    id: UUID @id @auto\n}");
        }

        // 9. Generic Fallback
        return new Explanation(
                errorCode != null && !errorCode.isEmpty() ? errorCode : "ZEN-999",
                "Compiler Diagnostic",
                errorMessage != null ? errorMessage : "An error occurred during compilation.",
                "Review the Zenith Language Specification via 'zenith_get_specification' for correct syntax.",
                null
        );
    }
}

