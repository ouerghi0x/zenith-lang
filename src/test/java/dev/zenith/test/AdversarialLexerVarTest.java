package dev.zenith.test;

import dev.zenith.lexer.Lexer;
import dev.zenith.lexer.Token;
import dev.zenith.lexer.TokenType;
import dev.zenith.parser.Parser;
import dev.zenith.ast.ZenithFile;

import java.util.ArrayList;
import java.util.List;

public class AdversarialLexerVarTest {

    private static int totalPassed = 0;
    private static int totalFailed = 0;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("=======================================================================");
        System.out.println("  EMPIRICAL ADVERSARIAL STRESS TEST: Milestone 1 Lexer (TokenType.VAR)");
        System.out.println("=======================================================================");

        testPrefixSuffixVariations();
        testCaseSensitivityVariations();
        testPartialWordBoundaries();
        testOperatorPunctuationAdjacency();
        testNumericAdjacency();
        testLayoutAndFormatting();
        testCommentsInteraction();
        testStringLiterals();
        testExactPositionTracking();
        testMixedTokenStreams();
        testHighVolumeStress();
        testParserBackwardCompatibilityStress();

        System.out.println("=======================================================================");
        System.out.printf("  TOTAL ADVERSARIAL TESTS: %d passed, %d failed%n", totalPassed, totalFailed);
        System.out.println("=======================================================================");

        if (totalFailed > 0) {
            System.err.println("FAILURES ENCOUNTERED:");
            for (String f : failures) {
                System.err.println("  - " + f);
            }
            System.exit(1);
        } else {
            System.out.println("ALL EMPIRICAL ADVERSARIAL TESTS PASSED CONVINCINGLY.");
        }
    }

    // --- CATEGORY 1: Prefix / Suffix Substring Resistance ---
    private static void testPrefixSuffixVariations() {
        System.out.println("\n[Suite 1] Prefix & Suffix Substring Resistance");

        String[] idVariations = {
            "variable", "variables", "variant", "variance", "various", "variety",
            "var1", "var0", "var99", "var_123", "var_name", "var_", "var__",
            "varVar", "varA", "varX", "var_val",
            "_var", "__var", "my_var", "myvar", "a_var_b", "invar", "ivar", "xvar",
            "is_valid_var", "env_var", "var_to_var", "var1var2", "shadow_var",
            "varIdentifier", "first_var_last"
        };

        for (String id : idVariations) {
            List<Token> tokens = new Lexer(id).tokenize();
            check(tokens.size() == 2, "Token count for '" + id + "' should be 2 (IDENTIFIER, EOF), got " + tokens.size());
            check(tokens.get(0).getType() == TokenType.IDENTIFIER,
                    "Expected IDENTIFIER for '" + id + "', got " + tokens.get(0).getType());
            check(tokens.get(0).getValue().equals(id),
                    "Expected value '" + id + "', got '" + tokens.get(0).getValue() + "'");
        }
    }

    // --- CATEGORY 2: Case Sensitivity Variations ---
    private static void testCaseSensitivityVariations() {
        System.out.println("\n[Suite 2] Case Sensitivity Variations");

        // Exact match
        List<Token> varTokens = new Lexer("var").tokenize();
        check(varTokens.get(0).getType() == TokenType.VAR, "Exact 'var' must be TokenType.VAR");
        check(varTokens.get(0).getValue().equals("var"), "Token text must be 'var'");

        // Case variations must NOT match keyword VAR
        String[] nonVarCasings = {"Var", "VAR", "vAr", "vaR", "VaR", "vAR", "VAr"};
        for (String casing : nonVarCasings) {
            List<Token> tokens = new Lexer(casing).tokenize();
            check(tokens.get(0).getType() == TokenType.IDENTIFIER,
                    "Casing '" + casing + "' must be IDENTIFIER, got " + tokens.get(0).getType());
            check(tokens.get(0).getValue().equals(casing),
                    "Expected token value '" + casing + "', got '" + tokens.get(0).getValue() + "'");
        }
    }

    // --- CATEGORY 3: Partial Word Boundaries (Shorter than "var") ---
    private static void testPartialWordBoundaries() {
        System.out.println("\n[Suite 3] Partial Word Boundaries");

        String[] partials = {"v", "va", "a", "r", "ar", "vr"};
        for (String p : partials) {
            List<Token> tokens = new Lexer(p).tokenize();
            check(tokens.get(0).getType() == TokenType.IDENTIFIER,
                    "Partial '" + p + "' must be IDENTIFIER, got " + tokens.get(0).getType());
            check(tokens.get(0).getValue().equals(p),
                    "Expected token value '" + p + "', got '" + tokens.get(0).getValue() + "'");
        }
    }

    // --- CATEGORY 4: Operator & Punctuation Adjacency Without Spaces ---
    private static void testOperatorPunctuationAdjacency() {
        System.out.println("\n[Suite 4] Operator & Punctuation Adjacency Without Spaces");

        // var followed directly by operator/delimiter
        assertTokenSequence("var=", TokenType.VAR, TokenType.EQUAL);
        assertTokenSequence("var==", TokenType.VAR, TokenType.EQUAL_EQUAL);
        assertTokenSequence("var!=", TokenType.VAR, TokenType.BANG_EQUAL);
        assertTokenSequence("var!", TokenType.VAR, TokenType.BANG);
        assertTokenSequence("var:", TokenType.VAR, TokenType.COLON);
        assertTokenSequence("var,", TokenType.VAR, TokenType.COMMA);
        assertTokenSequence("var.", TokenType.VAR, TokenType.DOT);
        assertTokenSequence("var.var", TokenType.VAR, TokenType.DOT, TokenType.VAR);
        assertTokenSequence("var.order", TokenType.VAR, TokenType.DOT, TokenType.IDENTIFIER);
        assertTokenSequence("order.var", TokenType.IDENTIFIER, TokenType.DOT, TokenType.VAR);
        assertTokenSequence("var?", TokenType.VAR, TokenType.QUESTION);
        assertTokenSequence("var@", TokenType.VAR, TokenType.AT);
        assertTokenSequence("var+", TokenType.VAR, TokenType.PLUS);
        assertTokenSequence("var-", TokenType.VAR, TokenType.MINUS);
        assertTokenSequence("var*", TokenType.VAR, TokenType.STAR);
        assertTokenSequence("var/", TokenType.VAR, TokenType.SLASH);
        assertTokenSequence("var->", TokenType.VAR, TokenType.ARROW);
        assertTokenSequence("var<", TokenType.VAR, TokenType.LT);
        assertTokenSequence("var>", TokenType.VAR, TokenType.GT);
        assertTokenSequence("var<=", TokenType.VAR, TokenType.LT_EQUAL);
        assertTokenSequence("var>=", TokenType.VAR, TokenType.GT_EQUAL);
        assertTokenSequence("var&&", TokenType.VAR, TokenType.AMP_AMP);
        assertTokenSequence("var||", TokenType.VAR, TokenType.PIPE_PIPE);

        // Delimiters wrapping or following var
        assertTokenSequence("(var)", TokenType.LPAREN, TokenType.VAR, TokenType.RPAREN);
        assertTokenSequence("{var}", TokenType.LBRACE, TokenType.VAR, TokenType.RBRACE);
        assertTokenSequence("[var]", TokenType.LBRACKET, TokenType.VAR, TokenType.RBRACKET);
        assertTokenSequence("var()", TokenType.VAR, TokenType.LPAREN, TokenType.RPAREN);
        assertTokenSequence("var{}", TokenType.VAR, TokenType.LBRACE, TokenType.RBRACE);
        assertTokenSequence("var[]", TokenType.VAR, TokenType.LBRACKET, TokenType.RBRACKET);

        // Preceding operator without spaces
        assertTokenSequence("=var", TokenType.EQUAL, TokenType.VAR);
        assertTokenSequence("==var", TokenType.EQUAL_EQUAL, TokenType.VAR);
        assertTokenSequence("!=var", TokenType.BANG_EQUAL, TokenType.VAR);
        assertTokenSequence("!var", TokenType.BANG, TokenType.VAR);
        assertTokenSequence(":var", TokenType.COLON, TokenType.VAR);
        assertTokenSequence(",var", TokenType.COMMA, TokenType.VAR);
        assertTokenSequence(".var", TokenType.DOT, TokenType.VAR);
        assertTokenSequence("+var", TokenType.PLUS, TokenType.VAR);
        assertTokenSequence("-var", TokenType.MINUS, TokenType.VAR);
        assertTokenSequence("*var", TokenType.STAR, TokenType.VAR);
        assertTokenSequence("/var", TokenType.SLASH, TokenType.VAR);
        assertTokenSequence("?var", TokenType.QUESTION, TokenType.VAR);
        assertTokenSequence("@var", TokenType.AT, TokenType.VAR);
        assertTokenSequence("->var", TokenType.ARROW, TokenType.VAR);
        assertTokenSequence("<var", TokenType.LT, TokenType.VAR);
        assertTokenSequence(">var", TokenType.GT, TokenType.VAR);
        assertTokenSequence("<=var", TokenType.LT_EQUAL, TokenType.VAR);
        assertTokenSequence(">=var", TokenType.GT_EQUAL, TokenType.VAR);
        assertTokenSequence("&&var", TokenType.AMP_AMP, TokenType.VAR);
        assertTokenSequence("||var", TokenType.PIPE_PIPE, TokenType.VAR);
    }

    // --- CATEGORY 5: Numeric Adjacency ---
    private static void testNumericAdjacency() {
        System.out.println("\n[Suite 5] Numeric Adjacency");

        // Digit followed immediately by var -> NUMBER_LITERAL then VAR
        assertTokenSequence("123var", TokenType.NUMBER_LITERAL, TokenType.VAR);
        assertTokenSequence("0var", TokenType.NUMBER_LITERAL, TokenType.VAR);
        assertTokenSequence("3.14var", TokenType.NUMBER_LITERAL, TokenType.VAR);

        // var followed immediately by digit -> single IDENTIFIER
        List<Token> varNum = new Lexer("var123").tokenize();
        check(varNum.get(0).getType() == TokenType.IDENTIFIER, "var123 must be IDENTIFIER");
        check(varNum.get(0).getValue().equals("var123"), "var123 value match");
    }

    // --- CATEGORY 6: Layout, Whitespace, & Formatting ---
    private static void testLayoutAndFormatting() {
        System.out.println("\n[Suite 6] Layout, Whitespace, & Formatting Variations");

        // Empty and whitespace only
        List<Token> emptyTokens = new Lexer("").tokenize();
        check(emptyTokens.size() == 1 && emptyTokens.get(0).getType() == TokenType.EOF, "Empty input -> EOF");

        List<Token> wsTokens = new Lexer("    \t\r\n \t\n  ").tokenize();
        check(wsTokens.size() == 1 && wsTokens.get(0).getType() == TokenType.EOF, "Whitespace-only input -> EOF");

        // Consecutive var tokens across various separators
        assertTokenSequence("var var var", TokenType.VAR, TokenType.VAR, TokenType.VAR);
        assertTokenSequence("var\nvar\nvar", TokenType.VAR, TokenType.VAR, TokenType.VAR);
        assertTokenSequence("var\r\nvar\r\nvar", TokenType.VAR, TokenType.VAR, TokenType.VAR);
        assertTokenSequence("var\t\tvar\t\tvar", TokenType.VAR, TokenType.VAR, TokenType.VAR);
        assertTokenSequence("   var   \n   var   \n   var   ", TokenType.VAR, TokenType.VAR, TokenType.VAR);
        assertTokenSequence("\n\n\nvar\n\n\n", TokenType.VAR);
        assertTokenSequence("\r\n\r\n\r\nvar\r\n\r\n\r\n", TokenType.VAR);
    }

    // --- CATEGORY 7: Comments Interaction ---
    private static void testCommentsInteraction() {
        System.out.println("\n[Suite 7] Comments Interaction");

        // Comments containing var
        List<Token> c1 = new Lexer("// var is a keyword").tokenize();
        check(c1.size() == 1 && c1.get(0).getType() == TokenType.EOF, "Single line comment only -> EOF");

        List<Token> c2 = new Lexer("/* var x = 123 */").tokenize();
        check(c2.size() == 1 && c2.get(0).getType() == TokenType.EOF, "Block comment only -> EOF");

        // Var adjacent to comments
        assertTokenSequence("// comment\nvar", TokenType.VAR);
        assertTokenSequence("// comment\r\nvar", TokenType.VAR);
        assertTokenSequence("var // trailing comment", TokenType.VAR);
        assertTokenSequence("/* leading */ var", TokenType.VAR);
        assertTokenSequence("var /* trailing */", TokenType.VAR);
        assertTokenSequence("/* start */ var /* middle */ = /* end */ create",
                TokenType.VAR, TokenType.EQUAL, TokenType.CREATE);

        // Multiline block comment with var keyword inside and outside
        String multilineComment = "/* \n * var a = 1\n * var b = 2\n */\nvar c = 3";
        assertTokenSequence(multilineComment, TokenType.VAR, TokenType.IDENTIFIER, TokenType.EQUAL, TokenType.NUMBER_LITERAL);

        // Block comment directly adjacent without space: var/*comment*/x
        assertTokenSequence("var/*comment*/x", TokenType.VAR, TokenType.IDENTIFIER);
        assertTokenSequence("/*comment*/var", TokenType.VAR);
    }

    // --- CATEGORY 8: String Literals Containing "var" ---
    private static void testStringLiterals() {
        System.out.println("\n[Suite 8] String Literals Containing 'var'");

        // Double quotes
        List<Token> s1 = new Lexer("\"var\"").tokenize();
        check(s1.size() == 2 && s1.get(0).getType() == TokenType.STRING_LITERAL, "Double quoted 'var' is STRING_LITERAL");
        check(s1.get(0).getValue().equals("var"), "String value is 'var'");

        // Single quotes
        List<Token> s2 = new Lexer("'var'").tokenize();
        check(s2.size() == 2 && s2.get(0).getType() == TokenType.STRING_LITERAL, "Single quoted 'var' is STRING_LITERAL");
        check(s2.get(0).getValue().equals("var"), "String value is 'var'");

        // Complex sentence inside string
        List<Token> s3 = new Lexer("\"var order = create Order { var: 'value' }\"").tokenize();
        check(s3.size() == 2 && s3.get(0).getType() == TokenType.STRING_LITERAL, "Full statement in string is STRING_LITERAL");

        // String adjacent to var
        assertTokenSequence("\"string\"var", TokenType.STRING_LITERAL, TokenType.VAR);
        assertTokenSequence("var\"string\"", TokenType.VAR, TokenType.STRING_LITERAL);
        assertTokenSequence("'string'var", TokenType.STRING_LITERAL, TokenType.VAR);
        assertTokenSequence("var'string'", TokenType.VAR, TokenType.STRING_LITERAL);

        // Escaped quotes inside string
        List<Token> s4 = new Lexer("\"test \\\"var\\\" inside\"").tokenize();
        check(s4.get(0).getType() == TokenType.STRING_LITERAL, "Escaped quotes string is STRING_LITERAL");
    }

    // --- CATEGORY 9: Exact Position Tracking ---
    private static void testExactPositionTracking() {
        System.out.println("\n[Suite 9] Exact Line and Column Position Tracking");

        // Basic line 1, col 1
        assertPos("var", 1, 1);

        // Leading spaces
        assertPos(" var", 1, 2);
        assertPos("   var", 1, 4);

        // Leading tabs
        assertPos("\tvar", 1, 2);
        assertPos("\t\tvar", 1, 3);

        // Newlines
        assertPos("\nvar", 2, 1);
        assertPos("\n\n\nvar", 4, 1);

        // Windows CRLF
        assertPos("\r\nvar", 2, 1);
        assertPos("\r\n\r\n\r\nvar", 4, 1);

        // After comment with newline
        assertPos("// comment\nvar", 2, 1);
        assertPos("// comment\r\nvar", 2, 1);

        // After inline block comment
        assertPos("/* 12345 */var", 1, 12);
        assertPos("/* 12345 */ var", 1, 13);

        // Multiline block comment
        assertPos("/* line1\nline2 */var", 2, 9);
        assertPos("/* line1\r\nline2 */var", 2, 9);

        // Multiple tokens on multiple lines
        List<Token> multi = new Lexer("config { }\n  var first\n    var second").tokenize();
        Token tok1 = multi.get(3); // var first
        check(tok1.getType() == TokenType.VAR && tok1.getLine() == 2 && tok1.getColumn() == 3,
                "First var at line 2 col 3, got " + tok1.getLine() + ":" + tok1.getColumn());
        Token tok2 = multi.get(5); // var second
        check(tok2.getType() == TokenType.VAR && tok2.getLine() == 3 && tok2.getColumn() == 5,
                "Second var at line 3 col 5, got " + tok2.getLine() + ":" + tok2.getColumn());
    }

    // --- CATEGORY 10: Mixed Token Streams ---
    private static void testMixedTokenStreams() {
        System.out.println("\n[Suite 10] Mixed Token Streams & Realistic Action Blocks");

        // create binding statement
        assertTokenSequence("var order = create Order { total: 100 }",
                TokenType.VAR, TokenType.IDENTIFIER, TokenType.EQUAL,
                TokenType.CREATE, TokenType.IDENTIFIER,
                TokenType.LBRACE, TokenType.IDENTIFIER, TokenType.COLON, TokenType.NUMBER_LITERAL,
                TokenType.RBRACE);

        // update binding statement
        assertTokenSequence("var item = update Product { price: 29.99 }",
                TokenType.VAR, TokenType.IDENTIFIER, TokenType.EQUAL,
                TokenType.UPDATE, TokenType.IDENTIFIER,
                TokenType.LBRACE, TokenType.IDENTIFIER, TokenType.COLON, TokenType.NUMBER_LITERAL,
                TokenType.RBRACE);

        // chained variable bindings with property access
        assertTokenSequence("var a = create A { }\nvar b = create B { aId: a.id }",
                TokenType.VAR, TokenType.IDENTIFIER, TokenType.EQUAL, TokenType.CREATE, TokenType.IDENTIFIER, TokenType.LBRACE, TokenType.RBRACE,
                TokenType.VAR, TokenType.IDENTIFIER, TokenType.EQUAL, TokenType.CREATE, TokenType.IDENTIFIER, TokenType.LBRACE,
                TokenType.IDENTIFIER, TokenType.COLON, TokenType.IDENTIFIER, TokenType.DOT, TokenType.IDENTIFIER,
                TokenType.RBRACE);

        // boolean expressions involving var
        assertTokenSequence("var flag = true && false || !x",
                TokenType.VAR, TokenType.IDENTIFIER, TokenType.EQUAL,
                TokenType.TRUE, TokenType.AMP_AMP, TokenType.FALSE, TokenType.PIPE_PIPE, TokenType.BANG, TokenType.IDENTIFIER);

        // comparisons
        assertTokenSequence("var valid = x <= 10 && y >= 20 && z == 30 && w != 40",
                TokenType.VAR, TokenType.IDENTIFIER, TokenType.EQUAL,
                TokenType.IDENTIFIER, TokenType.LT_EQUAL, TokenType.NUMBER_LITERAL, TokenType.AMP_AMP,
                TokenType.IDENTIFIER, TokenType.GT_EQUAL, TokenType.NUMBER_LITERAL, TokenType.AMP_AMP,
                TokenType.IDENTIFIER, TokenType.EQUAL_EQUAL, TokenType.NUMBER_LITERAL, TokenType.AMP_AMP,
                TokenType.IDENTIFIER, TokenType.BANG_EQUAL, TokenType.NUMBER_LITERAL);
    }

    // --- CATEGORY 11: High Volume / Stress Performance ---
    private static void testHighVolumeStress() {
        System.out.println("\n[Suite 11] High Volume Stress Test (5,000 var declarations)");

        StringBuilder sb = new StringBuilder();
        int count = 5000;
        for (int i = 0; i < count; i++) {
            sb.append("var item_").append(i).append(" = create Item { index: ").append(i).append(" }\n");
        }

        long startTime = System.currentTimeMillis();
        List<Token> tokens = new Lexer(sb.toString()).tokenize();
        long duration = System.currentTimeMillis() - startTime;

        // Each line has: VAR, IDENTIFIER, EQUAL, CREATE, IDENTIFIER, LBRACE, IDENTIFIER, COLON, NUMBER_LITERAL, RBRACE (10 tokens)
        // Plus 1 EOF token at the end
        int expectedTokens = count * 10 + 1;
        check(tokens.size() == expectedTokens,
                "Expected " + expectedTokens + " tokens in stress test, got " + tokens.size());
        check(duration < 2000,
                "Stress test should complete in < 2000ms, took " + duration + "ms");

        // Verify that every 10th token starting from index 0 is TokenType.VAR
        boolean allVarMatch = true;
        for (int i = 0; i < count; i++) {
            Token t = tokens.get(i * 10);
            if (t.getType() != TokenType.VAR || !t.getValue().equals("var")) {
                allVarMatch = false;
                break;
            }
        }
        check(allVarMatch, "All 5,000 var keywords resolved accurately in high volume test");
        System.out.printf("  Processed %d tokens in %d ms (%.1f tokens/ms)%n",
                tokens.size(), duration, (double) tokens.size() / Math.max(1, duration));
    }

    // --- CATEGORY 12: Parser Backward Compatibility Stress ---
    private static void testParserBackwardCompatibilityStress() {
        System.out.println("\n[Suite 12] Parser Backward Compatibility Stress Tests");

        // 1. Model with field named 'var' having annotations @id @auto
        testParser("Model with field 'var @id @auto'",
                "config { app: \"App1\" }\n" +
                "model Item {\n" +
                "    var: Long @id @auto\n" +
                "    title: String\n" +
                "}");

        // 2. Model with field named 'var' having default value and validation
        testParser("Model with field 'var' with multiple annotations",
                "config { app: \"App2\" }\n" +
                "model ConfigSetting {\n" +
                "    id: Long @id @auto\n" +
                "    var: String @unique @nullable @default(\"default_value\")\n" +
                "}");

        // 3. Model with relation named 'var' (@manyToOne and @oneToMany)
        testParser("Model with relations named 'var'",
                "config { app: \"App3\" }\n" +
                "model Parent {\n" +
                "    id: Long @id @auto\n" +
                "    relations {\n" +
                "        var: Child @oneToMany\n" +
                "    }\n" +
                "}\n" +
                "model Child {\n" +
                "    id: Long @id @auto\n" +
                "    relations {\n" +
                "        var: Parent @manyToOne\n" +
                "    }\n" +
                "}");

        // 4. Multiple models, each having a field named 'var'
        testParser("Multiple models each with field 'var'",
                "config { app: \"App4\" }\n" +
                "model Alpha {\n" +
                "    id: Long @id @auto\n" +
                "    var: Int\n" +
                "}\n" +
                "model Beta {\n" +
                "    id: Long @id @auto\n" +
                "    var: Boolean\n" +
                "}\n" +
                "model Gamma {\n" +
                "    id: Long @id @auto\n" +
                "    var: DateTime\n" +
                "}");

        // 5. DTO with field 'var'
        testParser("DTO with field 'var'",
                "config { app: \"App5\" }\n" +
                "dto UpdateVarRequest {\n" +
                "    var: String\n" +
                "    extra: Int\n" +
                "}");

        // 6. Seed with key 'var'
        testParser("Seed data with key 'var'",
                "config { app: \"App6\" }\n" +
                "model Entry {\n" +
                "    id: Long @id @auto\n" +
                "    var: String\n" +
                "}\n" +
                "seed Entry [\n" +
                "    { var: \"entry1\" },\n" +
                "    { var: \"entry2\" }\n" +
                "]");

        // 7. Full complex schema with CRUD, DTO, relations, seed all featuring 'var'
        testParser("Complex schema combining var field, relation, dto, and seed",
                "config {\n" +
                "    app: \"EnterpriseSuite\"\n" +
                "    database: postgres\n" +
                "}\n" +
                "model Category {\n" +
                "    id: Long @id @auto\n" +
                "    name: String\n" +
                "}\n" +
                "model Product {\n" +
                "    id: Long @id @auto\n" +
                "    var: String @unique\n" +
                "    price: Double\n" +
                "    relations {\n" +
                "        var: Category @manyToOne\n" +
                "    }\n" +
                "    endpoints {\n" +
                "        crud {\n" +
                "            paginate: true\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "dto ProductVarDto {\n" +
                "    var: String\n" +
                "}\n" +
                "seed Product [\n" +
                "    { var: \"PROD-001\", price: 99.99 }\n" +
                "]");
    }

    // --- Helper assertions ---

    private static void assertTokenSequence(String source, TokenType... expectedTypes) {
        List<Token> tokens = new Lexer(source).tokenize();
        // Disregard final EOF token for comparison convenience if not specified
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

    private static void assertPos(String source, int expectedLine, int expectedCol) {
        List<Token> tokens = new Lexer(source).tokenize();
        Token t = tokens.get(0);
        boolean ok = t.getType() == TokenType.VAR && t.getLine() == expectedLine && t.getColumn() == expectedCol;
        check(ok, "Position for '" + escape(source) + "': expected " + expectedLine + ":" + expectedCol
                + ", got " + t.getLine() + ":" + t.getColumn() + " (" + t.getType() + ")");
    }

    private static void testParser(String label, String dsl) {
        try {
            List<Token> tokens = new Lexer(dsl).tokenize();
            ZenithFile file = new Parser(tokens, "adversarial_test.zen").parse();
            check(file != null, "Parser accepted: " + label);
        } catch (Exception e) {
            check(false, "Parser rejected: " + label + " with error: " + e.getMessage());
        }
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
