package dev.zenith.test;

import dev.zenith.analyzer.SemanticAnalyzer;
import dev.zenith.ast.AnnotationDef;
import dev.zenith.ast.TypeRef;
import dev.zenith.ast.ZenithFile;
import dev.zenith.lexer.Lexer;
import dev.zenith.lexer.Token;
import dev.zenith.parser.Parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AdversarialParserCompatibilityTest:
 * Empirical adversarial harness testing parser keyword compatibility for 'var'
 * across all DSL construct positions with deep AST assertions and semantic analysis.
 */
public class AdversarialParserCompatibilityTest {

    public static class Scenario {
        public final String name;
        public final String code;
        public final Consumer<ZenithFile> validator;

        public Scenario(String name, String code, Consumer<ZenithFile> validator) {
            this.name = name;
            this.code = code;
            this.validator = validator;
        }
    }

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("  Adversarial Parser Keyword Compatibility Test Suite (Milestone 1)");
        System.out.println("==================================================================");

        Map<String, Scenario> scenarios = new LinkedHashMap<>();

        // 1. Model field named 'var'
        scenarios.put("Model field 'var: String'", new Scenario(
                "Model field 'var: String'",
                "config { app: \"AdvApp\" }\n" +
                "model Item {\n" +
                "    id: Long @id @auto\n" +
                "    var: String\n" +
                "}",
                ast -> {
                    if (ast.getModels().get(0).findField("var") == null) {
                        throw new AssertionError("Field 'var' not found in model Item");
                    }
                }
        ));

        // 2. Relation named 'var'
        scenarios.put("Relations block 'var: Category @manyToOne'", new Scenario(
                "Relations block 'var: Category @manyToOne'",
                "config { app: \"AdvApp\" }\n" +
                "model Category { id: Long @id @auto }\n" +
                "model Item {\n" +
                "    id: Long @id @auto\n" +
                "    relations {\n" +
                "        var: Category @manyToOne\n" +
                "    }\n" +
                "}",
                ast -> {
                    if (ast.getModels().get(1).findRelation("var") == null) {
                        throw new AssertionError("Relation 'var' not found in model Item");
                    }
                }
        ));

        // 3. Seed key named 'var'
        scenarios.put("Seed key '{ var: \"test\" }'", new Scenario(
                "Seed key '{ var: \"test\" }'",
                "config { app: \"AdvApp\" }\n" +
                "model Item { id: Long @id @auto \n var: String }\n" +
                "seed Item [\n" +
                "    { var: \"test\" }\n" +
                "]",
                ast -> {
                    if (!ast.getSeeds().get(0).getRows().get(0).containsKey("var")) {
                        throw new AssertionError("Seed row does not contain key 'var'");
                    }
                }
        ));

        // 4. DTO field named 'var'
        scenarios.put("DTO field 'var: Int'", new Scenario(
                "DTO field 'var: Int'",
                "config { app: \"AdvApp\" }\n" +
                "dto ItemDto {\n" +
                "    var: Int\n" +
                "}",
                ast -> {
                    if (ast.getDtos().get(0).getFields().stream().noneMatch(f -> "var".equals(f.getName()))) {
                        throw new AssertionError("Field 'var' not found in DTO ItemDto");
                    }
                }
        ));

        // 5. Inline relation named 'var'
        scenarios.put("Inline relation 'var: Category @relation(type = \"manyToOne\")'", new Scenario(
                "Inline relation 'var: Category @relation(type = \"manyToOne\")'",
                "config { app: \"AdvApp\" }\n" +
                "model Category { id: Long @id @auto }\n" +
                "model Item {\n" +
                "    id: Long @id @auto\n" +
                "    var: Category @relation(type = \"manyToOne\")\n" +
                "}",
                ast -> {
                    if (ast.getModels().get(1).findRelation("var") == null) {
                        throw new AssertionError("Inline relation 'var' not found in model Item");
                    }
                }
        ));

        // 6. Annotation argument key named 'var'
        scenarios.put("Annotation argument key 'var = \"value\"'", new Scenario(
                "Annotation argument key 'var = \"value\"'",
                "config { app: \"AdvApp\" }\n" +
                "model Item {\n" +
                "    id: Long @id @auto\n" +
                "    name: String @custom(var = \"value\")\n" +
                "}",
                ast -> {
                    AnnotationDef ann = ast.getModels().get(0).findField("name").getAnnotation("custom");
                    if (ann == null || !"value".equals(ann.getArgument("var"))) {
                        throw new AssertionError("Annotation argument 'var = value' not matched, found: " + ann);
                    }
                }
        ));

        // 7. Enum variant named 'var'
        scenarios.put("Enum variant 'var' in enum Status", new Scenario(
                "Enum variant 'var' in enum Status",
                "config { app: \"AdvApp\" }\n" +
                "enum Status {\n" +
                "    active,\n" +
                "    var,\n" +
                "    inactive\n" +
                "}\n" +
                "model Item {\n" +
                "    id: Long @id @auto\n" +
                "    status: Status\n" +
                "}",
                ast -> {
                    if (!ast.getEnums().get(0).getValues().contains("var")) {
                        throw new AssertionError("Enum variant 'var' not found in enum Status");
                    }
                }
        ));

        // 8. Parameterized type argument key 'var = 10'
        scenarios.put("Type parameter argument key 'String(var = 10)'", new Scenario(
                "Type parameter argument key 'String(var = 10)'",
                "config { app: \"AdvApp\" }\n" +
                "model Item {\n" +
                "    id: Long @id @auto\n" +
                "    code: String(var = 10)\n" +
                "}",
                ast -> {
                    TypeRef type = ast.getModels().get(0).findField("code").getType();
                    if (!type.getParams().containsKey("var")) {
                        throw new AssertionError("Type parameter 'var' not found in field code: " + type.getParams());
                    }
                }
        ));

        // 9. Seed value referencing identifier/enum 'var'
        scenarios.put("Seed unquoted value '{ status: var }'", new Scenario(
                "Seed unquoted value '{ status: var }'",
                "config { app: \"AdvApp\" }\n" +
                "enum Status { active, pending }\n" +
                "model Item {\n" +
                "    id: Long @id @auto\n" +
                "    status: Status\n" +
                "}\n" +
                "seed Item [\n" +
                "    { status: var }\n" +
                "]",
                ast -> {
                    Object val = ast.getSeeds().get(0).getRows().get(0).get("status");
                    if (!"var".equals(val)) {
                        throw new AssertionError("Seed status value does not equal 'var', found: " + val);
                    }
                }
        ));

        // 10. Annotation name named 'var'
        scenarios.put("Annotation name '@var(foo = \"bar\")'", new Scenario(
                "Annotation name '@var(foo = \"bar\")'",
                "config { app: \"AdvApp\" }\n" +
                "model Item {\n" +
                "    id: Long @id @auto\n" +
                "    name: String @var(foo = \"bar\")\n" +
                "}",
                ast -> {
                    if (!ast.getModels().get(0).findField("name").hasAnnotation("var")) {
                        throw new AssertionError("Annotation '@var' not found on field name");
                    }
                }
        ));

        // 11. Enum definition named 'var'
        scenarios.put("Enum name 'enum var { A, B }'", new Scenario(
                "Enum name 'enum var { A, B }'",
                "config { app: \"AdvApp\" }\n" +
                "enum var {\n" +
                "    FIRST,\n" +
                "    SECOND\n" +
                "}",
                ast -> {
                    if (ast.getEnums().stream().noneMatch(e -> "var".equals(e.getName()))) {
                        throw new AssertionError("Enum 'var' not found in AST");
                    }
                }
        ));

        // 12. Model definition named 'var'
        scenarios.put("Model name 'model var { ... }'", new Scenario(
                "Model name 'model var { ... }'",
                "config { app: \"AdvApp\" }\n" +
                "model var {\n" +
                "    id: Long @id @auto\n" +
                "}",
                ast -> {
                    if (ast.getModels().stream().noneMatch(m -> "var".equals(m.getName()))) {
                        throw new AssertionError("Model 'var' not found in AST");
                    }
                }
        ));

        int total = scenarios.size();
        int passCount = 0;
        int failCount = 0;
        SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer();

        for (Map.Entry<String, Scenario> entry : scenarios.entrySet()) {
            String name = entry.getKey();
            Scenario scenario = entry.getValue();
            try {
                Lexer lexer = new Lexer(scenario.code);
                List<Token> tokens = lexer.tokenize();
                Parser parser = new Parser(tokens, "adversarial_test.zen");
                ZenithFile ast = parser.parse();
                if (ast == null) {
                    throw new AssertionError("Parser returned null AST");
                }

                // 1. Deep structural AST verification
                scenario.validator.accept(ast);

                // 2. Semantic analysis verification
                semanticAnalyzer.analyze(ast);

                System.out.printf("[PARSED & ASSERTED OK] %-55s%n", name);
                passCount++;
            } catch (Throwable e) {
                System.out.printf("[FAILED] %-55s -> %s%n", name, e.getMessage());
                failCount++;
            }
        }

        System.out.println("==================================================================");
        System.out.printf("Summary: %d scenarios tested: %d passed, %d failed%n",
                total, passCount, failCount);
        System.out.println("==================================================================");

        if (failCount > 0) {
            System.err.printf("[FATAL] %d of %d adversarial scenarios failed!%n", failCount, total);
            System.exit(1);
        } else {
            System.out.println(">> ALL 12 ADVERSARIAL PARSER SCENARIOS PASSED WITH FULL AST ASSERTIONS!");
            System.exit(0);
        }
    }
}
