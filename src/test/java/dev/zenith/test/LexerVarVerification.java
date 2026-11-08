package dev.zenith.test;

import dev.zenith.lexer.Lexer;
import dev.zenith.lexer.Token;
import dev.zenith.lexer.TokenType;
import dev.zenith.parser.Parser;
import dev.zenith.ast.ZenithFile;

import java.util.List;

public class LexerVarVerification {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  Milestone 1: Lexer & Token Recognition Tests   ");
        System.out.println("=================================================");

        // --- SECTION 1: Direct Lexer Token Recognition ---

        // Test 1: Isolated 'var' keyword
        assertTokens("var", TokenType.VAR, TokenType.EOF);

        // Test 2: Variable declaration with create
        assertTokens("var order = create Order { }",
                TokenType.VAR, TokenType.IDENTIFIER, TokenType.EQUAL,
                TokenType.CREATE, TokenType.IDENTIFIER,
                TokenType.LBRACE, TokenType.RBRACE, TokenType.EOF);

        // Test 3: Variable declaration with update
        assertTokens("var item = update Product { }",
                TokenType.VAR, TokenType.IDENTIFIER, TokenType.EQUAL,
                TokenType.UPDATE, TokenType.IDENTIFIER,
                TokenType.LBRACE, TokenType.RBRACE, TokenType.EOF);

        // Test 4: Substring resistance - prefix
        assertIdentifier("variable", "variable");

        // Test 5: Substring resistance - suffix
        assertIdentifier("my_var", "my_var");

        // Test 6: Substring resistance - alphanumeric
        assertIdentifier("var123", "var123");

        // Test 7: Substring resistance - leading underscore
        assertIdentifier("_var", "_var");

        // Test 8: Case sensitivity (Zenith keywords are strictly lowercase)
        assertIdentifier("Var", "Var");
        assertIdentifier("VAR", "VAR");

        // Test 9: Whitespace & Line/Column tracking
        List<Token> tokens = new Lexer("   var").tokenize();
        assertEqual(tokens.get(0).getType(), TokenType.VAR, "Leading whitespace type");
        assertEqual(tokens.get(0).getLine(), 1, "Leading whitespace line");
        assertEqual(tokens.get(0).getColumn(), 4, "Leading whitespace column");

        // Test 10: Comments preceding 'var'
        List<Token> commentTokens = new Lexer("// comment\nvar x = 1").tokenize();
        assertEqual(commentTokens.get(0).getType(), TokenType.VAR, "Comment newline type");
        assertEqual(commentTokens.get(0).getLine(), 2, "Comment newline line");
        assertEqual(commentTokens.get(0).getColumn(), 1, "Comment newline column");

        // Test 11: Dot navigation property access
        assertTokens("order.total",
                TokenType.IDENTIFIER, TokenType.DOT, TokenType.IDENTIFIER, TokenType.EOF);

        // Test 12: Sequential var declarations
        assertTokens("var a = 1\nvar b = 2",
                TokenType.VAR, TokenType.IDENTIFIER, TokenType.EQUAL, TokenType.NUMBER_LITERAL,
                TokenType.VAR, TokenType.IDENTIFIER, TokenType.EQUAL, TokenType.NUMBER_LITERAL,
                TokenType.EOF);

        // --- SECTION 2: Parser Compatibility Safeguards ---

        // Test 13: Parser accepts model field named 'var'
        testParserSuccess("Model with field named 'var'",
                "config { app: \"TestApp\" }\n" +
                "model Item {\n" +
                "    id: Long @id @auto\n" +
                "    var: String\n" +
                "}");

        // Test 14: Parser accepts relation field named 'var'
        testParserSuccess("Model with relation named 'var'",
                "config { app: \"TestApp\" }\n" +
                "model Category {\n" +
                "    id: Long @id @auto\n" +
                "}\n" +
                "model Item {\n" +
                "    id: Long @id @auto\n" +
                "    relations {\n" +
                "        var: Category @manyToOne\n" +
                "    }\n" +
                "}");

        // Test 15: Parser accepts seed object with key named 'var'
        testParserSuccess("Seed with key named 'var'",
                "config { app: \"TestApp\" }\n" +
                "model Item {\n" +
                "    id: Long @id @auto\n" +
                "    var: String\n" +
                "}\n" +
                "seed Item [\n" +
                "    { var: \"val1\" }\n" +
                "]");

        // Test 16: Parser accepts DTO field named 'var'
        testParserSuccess("DTO with field named 'var'",
                "config { app: \"TestApp\" }\n" +
                "dto ItemDto {\n" +
                "    var: String\n" +
                "}");

        // Test 17: Parser accepts default value or annotation argument using 'var'
        testParserSuccess("Annotation argument with 'var'",
                "config { app: \"TestApp\" }\n" +
                "model Item {\n" +
                "    id: Long @id @auto\n" +
                "    name: String @custom(field = var)\n" +
                "}");

        System.out.printf("%n=================================================%n");
        System.out.printf("  Results: %d passed, %d failed%n", passed, failed);
        System.out.printf("=================================================%n");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void assertTokens(String source, TokenType... expectedTypes) {
        List<Token> tokens = new Lexer(source).tokenize();
        if (tokens.size() != expectedTypes.length) {
            System.err.printf("[FAIL] '%s': expected %d tokens, got %d%n", source, expectedTypes.length, tokens.size());
            failed++;
            return;
        }
        for (int i = 0; i < expectedTypes.length; i++) {
            if (tokens.get(i).getType() != expectedTypes[i]) {
                System.err.printf("[FAIL] '%s' token %d: expected %s, got %s%n", source, i, expectedTypes[i], tokens.get(i).getType());
                failed++;
                return;
            }
        }
        System.out.printf("[PASS] Token stream matched for: '%s'%n", source.replace("\n", "\\n"));
        passed++;
    }

    private static void assertIdentifier(String source, String expectedValue) {
        List<Token> tokens = new Lexer(source).tokenize();
        if (tokens.size() >= 1 && tokens.get(0).getType() == TokenType.IDENTIFIER && tokens.get(0).getValue().equals(expectedValue)) {
            System.out.printf("[PASS] Identifier preserved: '%s'%n", source);
            passed++;
        } else {
            System.err.printf("[FAIL] Expected IDENTIFIER '%s', got %s ('%s')%n", expectedValue,
                    tokens.isEmpty() ? "none" : tokens.get(0).getType(),
                    tokens.isEmpty() ? "" : tokens.get(0).getValue());
            failed++;
        }
    }

    private static void assertEqual(Object actual, Object expected, String label) {
        if (actual != null && actual.equals(expected)) {
            System.out.printf("[PASS] %s: %s%n", label, actual);
            passed++;
        } else {
            System.err.printf("[FAIL] %s: expected %s, got %s%n", label, expected, actual);
            failed++;
        }
    }

    private static void testParserSuccess(String testName, String dslContent) {
        try {
            Lexer lexer = new Lexer(dslContent);
            List<Token> tokens = lexer.tokenize();
            Parser parser = new Parser(tokens, "test.zen");
            ZenithFile ast = parser.parse();
            if (ast != null) {
                System.out.printf("[PASS] Parser compatibility: %s%n", testName);
                passed++;
            } else {
                System.err.printf("[FAIL] Parser compatibility: %s (returned null AST)%n", testName);
                failed++;
            }
        } catch (Exception e) {
            System.err.printf("[FAIL] Parser compatibility: %s threw exception: %s%n", testName, e.getMessage());
            failed++;
        }
    }
}
