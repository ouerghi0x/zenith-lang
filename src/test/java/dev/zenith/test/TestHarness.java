package dev.zenith.test;

import dev.zenith.analyzer.AnalysisException;
import dev.zenith.analyzer.SemanticAnalyzer;
import dev.zenith.ast.*;
import dev.zenith.generator.JavaSourceFile;
import dev.zenith.generator.ProjectGenerator;
import dev.zenith.generator.ServiceGenerator;
import dev.zenith.lexer.Lexer;
import dev.zenith.lexer.Token;
import dev.zenith.lexer.TokenType;
import dev.zenith.parser.ParseException;
import dev.zenith.parser.Parser;

import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Pattern;

/**
 * TestHarness: Core testing oracle and execution harness for Zenith Feature #1.
 * Provides DSL builders, pipeline invocations, AST reflection helpers, and assertions.
 */
public class TestHarness {

    public static class TestResult {
        public final String testName;
        public final int tier;
        public final boolean passed;
        public final String failureMessage;
        public final long durationMs;

        public TestResult(String testName, int tier, boolean passed, String failureMessage, long durationMs) {
            this.testName = testName;
            this.tier = tier;
            this.passed = passed;
            this.failureMessage = failureMessage;
            this.durationMs = durationMs;
        }
    }

    public static String wrapInStandardSchema(String bodyContent) {
        return "config {\n" +
                "    app: \"TestApp\"\n" +
                "    package: \"dev.zenith.testapp\"\n" +
                "    basePath: \"/api/v1\"\n" +
                "    port: 8080\n" +
                "    database: postgres\n" +
                "}\n\n" + bodyContent;
    }

    public static String createOrderModelSchema(String actionBody) {
        return wrapInStandardSchema(
                "model Customer {\n" +
                "    id: UUID @id @auto\n" +
                "    email: String @email @unique\n" +
                "    fullName: String\n" +
                "    createdAt: DateTime @autoNow\n" +
                "    updatedAt: DateTime @autoUpdate\n" +
                "}\n\n" +
                "model Invoice {\n" +
                "    id: UUID @id @auto\n" +
                "    orderId: UUID\n" +
                "    amount: Decimal\n" +
                "    status: String\n" +
                "    createdAt: DateTime @autoNow\n" +
                "}\n\n" +
                "model Order {\n" +
                "    id: UUID @id @auto\n" +
                "    orderNumber: String @unique\n" +
                "    totalAmount: Decimal\n" +
                "    status: String = \"PENDING\"\n" +
                "    customerId: UUID?\n" +
                "    createdAt: DateTime @autoNow\n" +
                "    updatedAt: DateTime @autoUpdate\n" +
                "    relations {\n" +
                "        customer: Customer? @relation(manyToOne)\n" +
                "    }\n" +
                "    endpoints {\n" +
                "        post \"/execute\" -> OrderResponse {\n" +
                "            action: {\n" +
                actionBody + "\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}\n"
        );
    }

    public static List<Token> tokenize(String dsl) {
        return new Lexer(dsl).tokenize();
    }

    public static ZenithFile parse(String dsl) {
        List<Token> tokens = tokenize(dsl);
        return new Parser(tokens, "test-schema.zen").parse();
    }

    public static ZenithFile analyze(String dsl) {
        ZenithFile file = parse(dsl);
        return new SemanticAnalyzer().analyze(file);
    }

    public static String generateServiceImplementation(String dsl, String modelName) {
        ZenithFile file = analyze(dsl);
        ModelDef targetModel = null;
        for (ModelDef m : file.getModels()) {
            if (m.getName().equalsIgnoreCase(modelName)) {
                targetModel = m;
                break;
            }
        }
        if (targetModel == null) {
            throw new IllegalArgumentException("Model not found in schema: " + modelName);
        }
        List<JavaSourceFile> sources = ServiceGenerator.generate(targetModel, file);
        for (JavaSourceFile src : sources) {
            if (src.getRelativePath().endsWith("ServiceImpl.java")) {
                return src.getContent();
            }
        }
        // Fallback to first source if not found
        return sources.isEmpty() ? "" : sources.get(sources.size() - 1).getContent();
    }

    public static List<JavaSourceFile> generateAllSources(String dsl) {
        ZenithFile file = analyze(dsl);
        return ProjectGenerator.generateAll(file);
    }

    // --- Assertions ---

    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }

    public static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError("Assertion failed (expected false): " + message);
        }
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Assertion failed: " + message + " [Expected: " + expected + ", Actual: " + actual + "]");
        }
    }

    public static void assertNotNull(Object val, String message) {
        if (val == null) {
            throw new AssertionError("Assertion failed: " + message + " (was null)");
        }
    }

    public static void assertContains(String text, String substring, String message) {
        if (text == null || !text.contains(substring)) {
            throw new AssertionError("Assertion failed: " + message + " - Text does not contain expected substring [" + substring + "]");
        }
    }

    public static void assertNotContains(String text, String substring, String message) {
        if (text != null && text.contains(substring)) {
            throw new AssertionError("Assertion failed: " + message + " - Text unexpectedly contains substring [" + substring + "]");
        }
    }

    public static void assertMatches(String text, String regex, String message) {
        if (text == null || !Pattern.compile(regex).matcher(text).find()) {
            throw new AssertionError("Assertion failed: " + message + " - Text does not match regex [" + regex + "]");
        }
    }

    /**
     * Inspects action statements for variable bindings using reflection to support
     * both pre-M2 and post-M2 compiler states.
     */
    public static boolean hasVariableBindingAST(CustomEndpointDef endpoint, String varName, String actionType, String targetModel) {
        try {
            // Check if getActions() exists (added in M2)
            Method getActionsMethod = endpoint.getClass().getMethod("getActions");
            @SuppressWarnings("unchecked")
            List<?> actions = (List<?>) getActionsMethod.invoke(endpoint);
            for (Object action : actions) {
                if (action == null) continue;
                String className = action.getClass().getSimpleName();
                if ("VariableBindingActionDef".equals(className)) {
                    Method getVarName = action.getClass().getMethod("getVariableName");
                    Method getActionType = action.getClass().getMethod("getActionType");
                    Method getTargetModel = action.getClass().getMethod("getTargetModel");
                    String actualVar = (String) getVarName.invoke(action);
                    String actualType = (String) getActionType.invoke(action);
                    String actualModel = (String) getTargetModel.invoke(action);
                    if (Objects.equals(varName, actualVar)
                            && (actionType == null || Objects.equals(actionType, actualType))
                            && (targetModel == null || Objects.equals(targetModel, actualModel))) {
                        return true;
                    }
                }
            }
        } catch (NoSuchMethodException ignored) {
            // Pre-M2: check raw action statements
            for (String stmt : endpoint.getActionStatements()) {
                if (stmt.trim().startsWith("var " + varName) && stmt.contains(actionType) && stmt.contains(targetModel)) {
                    return true;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error inspecting endpoint action AST", e);
        }
        return false;
    }
}
