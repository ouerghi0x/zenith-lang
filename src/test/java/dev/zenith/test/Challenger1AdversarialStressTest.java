package dev.zenith.test;

import dev.zenith.lexer.Lexer;
import dev.zenith.lexer.Token;
import dev.zenith.lexer.TokenType;
import dev.zenith.parser.Parser;
import dev.zenith.ast.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Challenger1AdversarialStressTest
 * 
 * Empirical adversarial stress harness for Milestone 1 Iteration 2:
 * 1. Token Delimiter and Boundary Stress
 * 2. Compound Identifier & Token-Bleed Resistance
 * 3. Parser Keyword Fallback & AST Fidelity Stress
 * 4. Parser Error Safeguards & Exception Handling
 * 5. Concurrent Reentrancy & Thread Safety
 * 6. High-Throughput Scale & Zero Token-Bleed Verification
 */
public class Challenger1AdversarialStressTest {

    private static int totalPassed = 0;
    private static int totalFailed = 0;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("=======================================================================");
        System.out.println("  CHALLENGER 1 EMPIRICAL ADVERSARIAL STRESS SUITE (Milestone 1 It 2)   ");
        System.out.println("=======================================================================");

        long startTime = System.currentTimeMillis();

        testDelimiterAndBoundaryStress();
        testCompoundIdentifierBleedStress();
        testParserKeywordFallbackStress();
        testParserErrorSafeguards();
        testConcurrencyAndReentrancy();
        testHighThroughputScaleAndZeroBleed();

        long duration = System.currentTimeMillis() - startTime;

        System.out.println("=======================================================================");
        System.out.printf("  CHALLENGER 1 RESULTS: %d passed, %d failed in %d ms%n",
                totalPassed, totalFailed, duration);
        System.out.println("=======================================================================");

        if (totalFailed > 0) {
            System.err.println("FAILURES DETECTED IN ADVERSARIAL CHALLENGE:");
            for (String f : failures) {
                System.err.println("  - " + f);
            }
            System.exit(1);
        } else {
            System.out.println(">> ALL ADVERSARIAL CHALLENGES PASSED EMPIRICALLY WITH ZERO DEFECTS.");
            System.exit(0);
        }
    }

    // -------------------------------------------------------------------------
    // 1. Delimiter and Boundary Stress
    // -------------------------------------------------------------------------
    private static void testDelimiterAndBoundaryStress() {
        System.out.println("\n[Challenge 1] Delimiter & Boundary Stress");

        // var immediately surrounded by multiple brackets and parentheses
        assertSequence("((((var))))",
                TokenType.LPAREN, TokenType.LPAREN, TokenType.LPAREN, TokenType.LPAREN,
                TokenType.VAR,
                TokenType.RPAREN, TokenType.RPAREN, TokenType.RPAREN, TokenType.RPAREN);

        assertSequence("[[[[var]]]]",
                TokenType.LBRACKET, TokenType.LBRACKET, TokenType.LBRACKET, TokenType.LBRACKET,
                TokenType.VAR,
                TokenType.RBRACKET, TokenType.RBRACKET, TokenType.RBRACKET, TokenType.RBRACKET);

        assertSequence("{{{{var}}}}",
                TokenType.LBRACE, TokenType.LBRACE, TokenType.LBRACE, TokenType.LBRACE,
                TokenType.VAR,
                TokenType.RBRACE, TokenType.RBRACE, TokenType.RBRACE, TokenType.RBRACE);

        // Multiple dots, colons, arrows, comparisons
        assertSequence("var...var", TokenType.VAR, TokenType.DOT, TokenType.DOT, TokenType.DOT, TokenType.VAR);
        assertSequence("var:::var", TokenType.VAR, TokenType.COLON, TokenType.COLON, TokenType.COLON, TokenType.VAR);
        assertSequence("var->var->var", TokenType.VAR, TokenType.ARROW, TokenType.VAR, TokenType.ARROW, TokenType.VAR);
        assertSequence("var<=var>=var==var!=var",
                TokenType.VAR, TokenType.LT_EQUAL,
                TokenType.VAR, TokenType.GT_EQUAL,
                TokenType.VAR, TokenType.EQUAL_EQUAL,
                TokenType.VAR, TokenType.BANG_EQUAL,
                TokenType.VAR);

        // Arithmetic operators adjacent to var
        assertSequence("var+var-var*var/var",
                TokenType.VAR, TokenType.PLUS,
                TokenType.VAR, TokenType.MINUS,
                TokenType.VAR, TokenType.STAR,
                TokenType.VAR, TokenType.SLASH,
                TokenType.VAR);

        // Logic operators adjacent to var
        assertSequence("!var&&var||!var",
                TokenType.BANG, TokenType.VAR,
                TokenType.AMP_AMP, TokenType.VAR,
                TokenType.PIPE_PIPE, TokenType.BANG, TokenType.VAR);

        // Mixed questions and at signs
        assertSequence("@var?var", TokenType.AT, TokenType.VAR, TokenType.QUESTION, TokenType.VAR);
        assertSequence("var? @var", TokenType.VAR, TokenType.QUESTION, TokenType.AT, TokenType.VAR);

        // var at exact EOF without trailing newline or whitespace
        List<Token> eofTokens = new Lexer("var").tokenize();
        check(eofTokens.size() == 2 && eofTokens.get(0).getType() == TokenType.VAR && eofTokens.get(1).getType() == TokenType.EOF,
                "var at exact EOF produces VAR followed by EOF");

        // var at start of file without leading whitespace
        check(eofTokens.get(0).getLine() == 1 && eofTokens.get(0).getColumn() == 1,
                "var at start of file has line=1, column=1");
    }

    // -------------------------------------------------------------------------
    // 2. Compound Identifier & Token-Bleed Resistance
    // -------------------------------------------------------------------------
    private static void testCompoundIdentifierBleedStress() {
        System.out.println("\n[Challenge 2] Compound Identifier & Token-Bleed Resistance");

        // Test an extensive set of identifiers containing "var"
        String[] identifiers = {
            "var_", "_var", "__var__", "_var_", "var_a", "var_1", "var_00", "var_var",
            "variable", "variables", "variant", "variants", "variation", "variations",
            "variance", "variances", "varity", "variety", "various", "variously",
            "varA", "varB", "varCamelCase", "varPascalCase", "varUPPER",
            "Avar", "myVar", "oldVar", "newVar", "isVar", "hasVar", "getVar", "setVar",
            "invariant", "subvariant", "unvariable", "covariable", "multivariable",
            "VAR", "Var", "vAr", "vaR", "VaR", "vAR", "VAr",
            "var1", "var2", "var3", "var99999", "var0",
            "var_name_long_identifier_with_many_words_ending_in_var"
        };

        for (String id : identifiers) {
            List<Token> tokens = new Lexer(id).tokenize();
            check(tokens.size() == 2, "Identifier '" + id + "' produced exactly 1 token + EOF");
            check(tokens.get(0).getType() == TokenType.IDENTIFIER,
                    "Identifier '" + id + "' strictly tokenized as IDENTIFIER, not VAR");
            check(tokens.get(0).getValue().equals(id),
                    "Identifier '" + id + "' preserved exact text value");
        }

        // Test glued identifier sequences
        assertSequence("var _var var", TokenType.VAR, TokenType.IDENTIFIER, TokenType.VAR);
        assertSequence("var var_name = var", TokenType.VAR, TokenType.IDENTIFIER, TokenType.EQUAL, TokenType.VAR);
        assertSequence("var.var.var_id", TokenType.VAR, TokenType.DOT, TokenType.VAR, TokenType.DOT, TokenType.IDENTIFIER);
    }

    // -------------------------------------------------------------------------
    // 3. Parser Keyword Fallback & AST Fidelity Stress
    // -------------------------------------------------------------------------
    private static void testParserKeywordFallbackStress() {
        System.out.println("\n[Challenge 3] Parser Keyword Fallback & Full AST Fidelity");

        // Schema exercising 'var' across every single syntax slot in the Zenith DSL
        String fullVarDsl =
                "config {\n" +
                "    app: \"VarApp\"\n" +
                "    database: postgres\n" +
                "}\n" +
                "\n" +
                "enum var {\n" +
                "    var,\n" +
                "    standard\n" +
                "}\n" +
                "\n" +
                "model var {\n" +
                "    id: Long @id @auto\n" +
                "    var: String(var = 50) @unique @var(tag = \"test\")\n" +
                "    status: var\n" +
                "    defaultVal: String = var\n" +
                "    relations {\n" +
                "        var: var @manyToOne\n" +
                "    }\n" +
                "    endpoints {\n" +
                "        crud {\n" +
                "            paginate: true\n" +
                "            search: [var]\n" +
                "            sort: [var]\n" +
                "            filter: [var]\n" +
                "        }\n" +
                "        post \"/var\" (body: var) -> var {\n" +
                "            description: \"Custom endpoint with var slots\"\n" +
                "            guard: entity.var != null, \"Invalid var\"\n" +
                "            params: { var: var }\n" +
                "            action: {\n" +
                "                update var on \"var_coll\" { var: \"updated\" }\n" +
                "            }\n" +
                "        }\n" +
                "        get /all-vars -> List<var> {\n" +
                "            description: \"List vars\"\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "dto var {\n" +
                "    var: var\n" +
                "}\n" +
                "\n" +
                "seed var [\n" +
                "    { var: \"initial_val\", status: var }\n" +
                "]\n" +
                "\n" +
                "job var {\n" +
                "    cron: \"0 0 * * *\"\n" +
                "    action: {\n" +
                "        create var { var: \"cron_var\" }\n" +
                "    }\n" +
                "}\n";

        try {
            Lexer lexer = new Lexer(fullVarDsl);
            List<Token> tokens = lexer.tokenize();
            Parser parser = new Parser(tokens, "full_var_stress.zen");
            ZenithFile ast = parser.parse();

            check(ast != null, "Full 'var' schema parsed into non-null AST");

            // Verify AST Enum
            EnumDef enumDef = ast.getEnums().get(0);
            check("var".equals(enumDef.getName()), "Enum name is 'var'");
            check(enumDef.getValues().contains("var"), "Enum values contains 'var'");

            // Verify AST Model
            ModelDef modelDef = ast.getModels().get(0);
            check("var".equals(modelDef.getName()), "Model name is 'var'");
            FieldDef fieldDef = modelDef.findField("var");
            check(fieldDef != null, "Field 'var' exists in model 'var'");
            check(fieldDef.getType().getParams().containsKey("var"), "Type parameter 'var' parsed on field 'var'");
            check(fieldDef.hasAnnotation("var"), "Annotation '@var' parsed on field 'var'");
            check(fieldDef.getAnnotation("var").getArgument("tag").equals("test"), "Annotation argument parsed");

            FieldDef defaultValField = modelDef.findField("defaultVal");
            check("var".equals(defaultValField.getDefaultValue()), "Default value 'var' matched");

            // Verify AST Relation
            RelationDef relDef = modelDef.findRelation("var");
            check(relDef != null, "Relation 'var' exists in model 'var'");
            check("var".equals(relDef.getTargetModel()), "Relation target model is 'var'");

            // Verify CRUD search/sort/filter
            CrudConfig crud = modelDef.getCrudConfig();
            check(crud != null, "CRUD config present");
            check(crud.getSearchFields().contains("var"), "CRUD search contains 'var'");
            check(crud.getSortFields().contains("var"), "CRUD sort contains 'var'");
            check(crud.getFilterFields().contains("var"), "CRUD filter contains 'var'");

            // Verify Custom Endpoints
            List<CustomEndpointDef> endpoints = modelDef.getCustomEndpoints();
            check(endpoints.size() == 2, "Found 2 custom endpoints");
            CustomEndpointDef postEp = endpoints.get(0);
            check("/var".equals(postEp.getPath()), "Custom endpoint path is '/var'");
            check("var".equals(postEp.getBodyType()), "Custom endpoint body type is 'var'");
            check("var".equals(postEp.getReturnType()), "Custom endpoint return type is 'var'");
            check("var".equals(postEp.getParamTypes().get("var")), "Param 'var: var' parsed");
            check(postEp.getActionStatements().size() == 1, "Action statement parsed in post endpoint");
            check(postEp.getActionStatements().get(0).contains("var"), "Action statement contains 'var'");

            CustomEndpointDef getEp = endpoints.get(1);
            check("List<var>".equals(getEp.getReturnType()), "Custom endpoint return type is 'List<var>'");

            // Verify DTO
            DtoDef dtoDef = ast.getDtos().get(0);
            check("var".equals(dtoDef.getName()), "DTO name is 'var'");
            check("var".equals(dtoDef.getFields().get(0).getName()), "DTO field name is 'var'");
            check("var".equals(dtoDef.getFields().get(0).getType().getName()), "DTO field type is 'var'");

            // Verify Seed
            SeedDef seedDef = ast.getSeeds().get(0);
            check("var".equals(seedDef.getEntityName()), "Seed model name is 'var'");
            check("initial_val".equals(seedDef.getRows().get(0).get("var")), "Seed row key 'var' matched");
            check("var".equals(seedDef.getRows().get(0).get("status")), "Seed row value 'var' matched");

            // Verify Job
            JobDef jobDef = ast.getJobs().get(0);
            check("var".equals(jobDef.getName()), "Job name is 'var'");
            check(jobDef.getActionStatements().get(0).contains("create var"), "Job action statement matched");

        } catch (Exception e) {
            check(false, "Full var DSL parsing failed with exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // -------------------------------------------------------------------------
    // 4. Parser Error Safeguards & Exception Handling
    // -------------------------------------------------------------------------
    private static void testParserErrorSafeguards() {
        System.out.println("\n[Challenge 4] Parser Error Safeguards & Clean Rejections");

        // Malformed DSL snippets that MUST fail with ParseException or RuntimeException, not NPE
        String[] malformedSnippets = {
            "model { var }",                        // missing model name and body syntax
            "model Item { var }",                   // missing colon and type
            "enum { var }",                         // missing enum name
            "seed [ { var: 1 } ]",                  // missing seed model name
            "job { cron: \"* * * * *\" }",          // missing job name
            "config { var: }",                      // missing config value
            "model Item { name: String @var( }",    // unclosed annotation
            "\"unterminated string with var",       // unterminated string
        };

        for (int i = 0; i < malformedSnippets.length; i++) {
            String snippet = malformedSnippets[i];
            try {
                Lexer lexer = new Lexer(snippet);
                List<Token> tokens = lexer.tokenize();
                Parser parser = new Parser(tokens, "error_test_" + i + ".zen");
                parser.parse();
                check(false, "Malformed snippet #" + i + " should have thrown error but succeeded: '" + snippet + "'");
            } catch (Exception expected) {
                check(true, "Malformed snippet #" + i + " cleanly rejected with "
                        + expected.getClass().getSimpleName() + ": " + expected.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // 5. Concurrent Reentrancy & Thread Safety
    // -------------------------------------------------------------------------
    private static void testConcurrencyAndReentrancy() {
        System.out.println("\n[Challenge 5] Concurrent Reentrancy & Thread Safety");

        int threadCount = 10;
        int iterationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<String> threadErrors = Collections.synchronizedList(new ArrayList<>());

        String dslTemplate =
                "config { app: \"ThreadApp_%d\" }\n" +
                "model Item_%d {\n" +
                "    id: Long @id @auto\n" +
                "    var: String\n" +
                "    status: Int = var\n" +
                "}\n" +
                "seed Item_%d [ { var: \"val_%d\" } ]\n";

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        String dsl = String.format(dslTemplate, threadId, threadId, threadId, i);
                        Lexer lexer = new Lexer(dsl);
                        List<Token> tokens = lexer.tokenize();
                        Parser parser = new Parser(tokens, "thread_" + threadId + ".zen");
                        ZenithFile file = parser.parse();
                        if (file == null || file.getModels().isEmpty() || file.getModels().get(0).findField("var") == null) {
                            threadErrors.add("Thread " + threadId + " iteration " + i + ": AST invalid");
                        }
                    }
                } catch (Throwable th) {
                    threadErrors.add("Thread " + threadId + " failed with: " + th.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            boolean completed = latch.await(10, TimeUnit.SECONDS);
            check(completed, "All 10 concurrent threads finished within 10 seconds");
            check(threadErrors.isEmpty(), "Zero concurrent errors across " + (threadCount * iterationsPerThread)
                    + " parallel parse operations (found " + threadErrors.size() + ")");
            if (!threadErrors.isEmpty()) {
                for (String err : threadErrors) {
                    System.err.println("  [CONCURRENCY ERROR] " + err);
                }
            }
        } catch (InterruptedException e) {
            check(false, "Concurrency test interrupted");
        } finally {
            executor.shutdownNow();
        }
    }

    // -------------------------------------------------------------------------
    // 6. High-Throughput Scale & Zero Token-Bleed Verification
    // -------------------------------------------------------------------------
    private static void testHighThroughputScaleAndZeroBleed() {
        System.out.println("\n[Challenge 6] High-Throughput Scale & Zero Token-Bleed (10,000 Declarations)");

        int iterations = 10000;
        StringBuilder sb = new StringBuilder();
        sb.append("config { app: \"ScaleApp\" }\n");
        for (int i = 0; i < iterations; i++) {
            sb.append("model Model_").append(i).append(" {\n");
            sb.append("    id: Long @id @auto\n");
            sb.append("    var: String\n");
            sb.append("    var_").append(i).append(": Int\n");
            sb.append("}\n");
        }

        long startLex = System.currentTimeMillis();
        Lexer lexer = new Lexer(sb.toString());
        List<Token> tokens = lexer.tokenize();
        long lexDuration = System.currentTimeMillis() - startLex;

        // Verify total token count
        // config { app: "ScaleApp" } -> 6 tokens
        // each model: model, Name, {, id, :, Long, @, id, @, auto, var, :, String, var_i, :, Int, } -> 17 tokens
        // EOF -> 1 token
        // Total expected = 6 + iterations * 17 + 1
        int expectedTokens = 6 + (iterations * 17) + 1;
        check(tokens.size() == expectedTokens,
                "Scale test: expected " + expectedTokens + " tokens, got " + tokens.size());
        check(lexDuration < 1500,
                "Scale test lexing 10,000 models (< 1500ms), actual: " + lexDuration + " ms");

        // Verify zero token bleed across all tokens
        int varKeywordCount = 0;
        int varIdentifierCount = 0;
        boolean allVarValuesStrictlyMatch = true;
        for (Token tok : tokens) {
            if (tok.getType() == TokenType.VAR) {
                if (!"var".equals(tok.getValue())) {
                    allVarValuesStrictlyMatch = false;
                }
                varKeywordCount++;
            } else if (tok.getType() == TokenType.IDENTIFIER && tok.getValue().startsWith("var_")) {
                varIdentifierCount++;
            }
        }

        check(allVarValuesStrictlyMatch, "All TokenType.VAR token values strictly equal 'var'");
        check(varKeywordCount == iterations,
                "Exactly " + iterations + " TokenType.VAR tokens found (zero false positives/negatives), got " + varKeywordCount);
        check(varIdentifierCount == iterations,
                "Exactly " + iterations + " 'var_<index>' IDENTIFIER tokens found (zero bleed), got " + varIdentifierCount);

        System.out.printf("  Lexed %d tokens in %d ms (%.1f tokens/ms)%n",
                tokens.size(), lexDuration, (double) tokens.size() / Math.max(1, lexDuration));
    }

    // -------------------------------------------------------------------------
    // Helper assertions
    // -------------------------------------------------------------------------
    private static void assertSequence(String source, TokenType... expectedTypes) {
        List<Token> tokens = new Lexer(source).tokenize();
        int countToCompare = expectedTypes.length;
        if (tokens.size() - 1 != countToCompare) {
            check(false, "Source '" + escape(source) + "': expected " + countToCompare
                    + " tokens (excluding EOF), got " + (tokens.size() - 1));
            return;
        }
        for (int i = 0; i < countToCompare; i++) {
            if (tokens.get(i).getType() != expectedTypes[i]) {
                check(false, "Source '" + escape(source) + "' token " + i + ": expected "
                        + expectedTypes[i] + ", got " + tokens.get(i).getType());
                return;
            }
        }
        check(true, "Token sequence matched for: '" + escape(source) + "'");
    }

    private static void check(boolean condition, String description) {
        if (condition) {
            totalPassed++;
            System.out.println("  [PASS] " + description);
        } else {
            totalFailed++;
            failures.add(description);
            System.err.println("  [FAIL] " + description);
        }
    }

    private static String escape(String s) {
        return s.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
