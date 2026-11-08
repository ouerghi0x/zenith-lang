package dev.zenith.test;

import dev.zenith.lexer.Lexer;
import dev.zenith.lexer.Token;
import dev.zenith.parser.ParseException;
import dev.zenith.parser.Parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AdversarialInvalidSyntaxErrorTest:
 * Empirical adversarial harness verifying that invalid DSL constructs across all
 * grammar positions continue to produce proper line/column syntax errors.
 */
public class AdversarialInvalidSyntaxErrorTest {

    public static class NegativeCase {
        public final String name;
        public final String code;
        public final int expectedLine;
        public final int expectedCol;
        public final String expectedFragment;

        public NegativeCase(String name, String code, int expectedLine, int expectedCol, String expectedFragment) {
            this.name = name;
            this.code = code;
            this.expectedLine = expectedLine;
            this.expectedCol = expectedCol;
            this.expectedFragment = expectedFragment;
        }
    }

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("  Adversarial Invalid Syntax & Error Position Test Suite          ");
        System.out.println("==================================================================");

        Map<String, NegativeCase> cases = new LinkedHashMap<>();

        // 1. Missing model name
        cases.put("Missing model name", new NegativeCase(
                "Missing model name",
                "config { app: \"NegApp\" }\n" +
                "model {\n" +
                "    id: Long @id @auto\n" +
                "}",
                2, 7, "Expected model name"
        ));

        // 2. Missing enum name
        cases.put("Missing enum name", new NegativeCase(
                "Missing enum name",
                "config { app: \"NegApp\" }\n" +
                "enum {\n" +
                "    A, B\n" +
                "}",
                2, 6, "Expected enum name"
        ));

        // 3. Missing enum variant identifier
        cases.put("Missing enum variant identifier", new NegativeCase(
                "Missing enum variant identifier",
                "config { app: \"NegApp\" }\n" +
                "enum Status {\n" +
                "    ,\n" +
                "}",
                3, 5, "Expected enum value identifier"
        ));

        // 4. Missing annotation name after @ (unexpected token '}' at 4:1)
        cases.put("Missing annotation name after @", new NegativeCase(
                "Missing annotation name after @",
                "config { app: \"NegApp\" }\n" +
                "model Item {\n" +
                "    id: Long @\n" +
                "}",
                4, 1, "Expected annotation name"
        ));

        // 5. Annotation argument missing key (starts with =)
        cases.put("Annotation argument missing key", new NegativeCase(
                "Annotation argument missing key",
                "config { app: \"NegApp\" }\n" +
                "model Item {\n" +
                "    id: Long @id @auto\n" +
                "    name: String @custom(= \"val\")\n" +
                "}",
                4, 26, "Expected literal value or identifier, found EQUAL"
        ));

        // 6. Annotation unclosed parenthesis (unexpected token '}' at 5:1)
        cases.put("Annotation unclosed parenthesis", new NegativeCase(
                "Annotation unclosed parenthesis",
                "config { app: \"NegApp\" }\n" +
                "model Item {\n" +
                "    id: Long @id @auto\n" +
                "    name: String @custom(key = \"val\"\n" +
                "}",
                5, 1, "Syntax error"
        ));

        // 7. Parameterized type missing argument key
        cases.put("Parameterized type missing argument key", new NegativeCase(
                "Parameterized type missing argument key",
                "config { app: \"NegApp\" }\n" +
                "model Item {\n" +
                "    id: Long @id @auto\n" +
                "    code: String(= 10)\n" +
                "}",
                4, 18, "Expected literal value or identifier, found EQUAL"
        ));

        // 8. Seed value missing value after colon
        cases.put("Seed value missing value after colon", new NegativeCase(
                "Seed value missing value after colon",
                "config { app: \"NegApp\" }\n" +
                "model Item { id: Long @id @auto }\n" +
                "seed Item [\n" +
                "    { id: }\n" +
                "]",
                4, 11, "Expected seed value (string, number, boolean, or identifier), but found '}'"
        ));

        // 9. DTO missing name
        cases.put("DTO missing name", new NegativeCase(
                "DTO missing name",
                "config { app: \"NegApp\" }\n" +
                "dto {\n" +
                "    id: Int\n" +
                "}",
                2, 5, "Expected dto name"
        ));

        // 10. Relations block missing field name
        cases.put("Relations block missing field name", new NegativeCase(
                "Relations block missing field name",
                "config { app: \"NegApp\" }\n" +
                "model Category { id: Long @id @auto }\n" +
                "model Item {\n" +
                "    id: Long @id @auto\n" +
                "    relations {\n" +
                "        : Category @manyToOne\n" +
                "    }\n" +
                "}",
                6, 9, "Expected relation field name"
        ));

        // 11. Missing colon in field definition
        cases.put("Missing colon in field definition", new NegativeCase(
                "Missing colon in field definition",
                "config { app: \"NegApp\" }\n" +
                "model Item {\n" +
                "    id Long @id @auto\n" +
                "}",
                3, 8, "Expected ':' after field name"
        ));

        // 12. Top-level 'var' statement rejection
        cases.put("Top-level 'var' statement rejection", new NegativeCase(
                "Top-level 'var' statement rejection",
                "var Item {\n" +
                "    id: Long @id @auto\n" +
                "}",
                1, 1, "Expected 'import', 'config', 'enum', 'model', 'dto', 'seed', or 'job' but found 'var'"
        ));

        int total = cases.size();
        int passCount = 0;
        int failCount = 0;

        for (Map.Entry<String, NegativeCase> entry : cases.entrySet()) {
            String name = entry.getKey();
            NegativeCase nc = entry.getValue();
            try {
                Lexer lexer = new Lexer(nc.code);
                List<Token> tokens = lexer.tokenize();
                Parser parser = new Parser(tokens, "neg_test.zen");
                parser.parse();

                System.out.printf("[UNEXPECTED PASS - MUST FAIL] %-45s%n", name);
                failCount++;
            } catch (ParseException e) {
                boolean lineMatches = (e.getLine() == nc.expectedLine);
                boolean colMatches = (e.getColumn() == nc.expectedCol);
                boolean fragmentMatches = (nc.expectedFragment == null || e.getMessage().contains(nc.expectedFragment));

                if (lineMatches && colMatches && fragmentMatches) {
                    System.out.printf("[REJECTED AS EXPECTED (Exact %d:%d)] %-40s -> %s%n",
                            e.getLine(), e.getColumn(), name, e.getMessage().replaceAll("\r?\n", " "));
                    passCount++;
                } else {
                    System.out.printf("[POSITION MISMATCH] %-40s -> Expected %d:%d containing '%s', got %d:%d '%s'%n",
                            name, nc.expectedLine, nc.expectedCol, nc.expectedFragment, e.getLine(), e.getColumn(), e.getMessage());
                    failCount++;
                }
            } catch (Throwable t) {
                System.out.printf("[WRONG EXCEPTION] %-40s -> %s%n", name, t.toString());
                failCount++;
            }
        }

        System.out.println("==================================================================");
        System.out.printf("Summary: %d negative cases tested: %d passed, %d failed%n",
                total, passCount, failCount);
        System.out.println("==================================================================");

        if (failCount > 0) {
            System.err.printf("[FATAL] %d of %d negative tests failed!%n", failCount, total);
            System.exit(1);
        } else {
            System.out.println(">> ALL 12 ADVERSARIAL NEGATIVE SYNTAX TESTS REPORTED EXACT LINE/COL!");
            System.exit(0);
        }
    }
}
