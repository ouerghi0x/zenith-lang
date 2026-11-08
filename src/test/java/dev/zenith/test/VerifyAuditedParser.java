package dev.zenith.test;

import dev.zenith.lexer.Lexer;
import dev.zenith.lexer.Token;
import dev.zenith.parser.Parser;
import dev.zenith.ast.ZenithFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * VerifyAuditedParser:
 * Verifies that Parser.java safely and reliably parses all 43 edge-case scenarios
 * involving 'var' across all Zenith DSL grammar positions.
 */
public class VerifyAuditedParser {

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("  Verifying AuditedParser Safeguards on All 43 Scenarios");
        System.out.println("==================================================================");

        Map<String, String> scenarios = new LinkedHashMap<>();

        // Baseline / Challenger 2 scenarios
        scenarios.put("01. Model field 'var: String'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n var: String }");
        scenarios.put("02. Relations block 'var: Category @manyToOne'",
                "config { app: \"AdvApp\" }\nmodel Category { id: Long @id @auto }\nmodel Item { id: Long @id @auto \n relations { var: Category @manyToOne } }");
        scenarios.put("03. Seed key '{ var: \"test\" }'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n var: String }\nseed Item [ { var: \"test\" } ]");
        scenarios.put("04. DTO field 'var: Int'",
                "config { app: \"AdvApp\" }\ndto ItemDto { var: Int }");
        scenarios.put("05. Inline relation 'var: Category @relation(type = \"manyToOne\")'",
                "config { app: \"AdvApp\" }\nmodel Category { id: Long @id @auto }\nmodel Item { id: Long @id @auto \n var: Category @relation(type = \"manyToOne\") }");
        scenarios.put("06. Annotation argument key 'var = \"value\"'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n name: String @custom(var = \"value\") }");
        scenarios.put("07. Enum variant 'var' in enum Status",
                "config { app: \"AdvApp\" }\nenum Status { active, var, inactive }\nmodel Item { id: Long @id @auto \n status: Status }");
        scenarios.put("08. Parameterized type arg key 'String(var = 10)'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n code: String(var = 10) }");
        scenarios.put("09. Seed unquoted value '{ status: var }'",
                "config { app: \"AdvApp\" }\nenum Status { active, pending }\nmodel Item { id: Long @id @auto \n status: Status }\nseed Item [ { status: var } ]");
        scenarios.put("10. Annotation name '@var(foo = \"bar\")'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n name: String @var(foo = \"bar\") }");
        scenarios.put("11. Bare annotation name '@var'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n name: String @var }");
        scenarios.put("12. Enum definition name 'enum var { A, B }'",
                "config { app: \"AdvApp\" }\nenum var { FIRST, SECOND }");
        scenarios.put("13. Model definition name 'model var { ... }'",
                "config { app: \"AdvApp\" }\nmodel var { id: Long @id @auto }");
        scenarios.put("14. DTO definition name 'dto var { ... }'",
                "config { app: \"AdvApp\" }\ndto var { id: Long }");

        // Extended Seed Scenarios
        scenarios.put("15. Seed value dotted property access '{ ref: cat.var }'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n ref: String }\nseed Item [ { ref: cat.var } ]");
        scenarios.put("16. Seed value dotted start '{ ref: var.field }'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n ref: String }\nseed Item [ { ref: var.field } ]");
        scenarios.put("17. Seed value indexed '{ ref: var[0] }'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n ref: String }\nseed Item [ { ref: var[0] } ]");
        scenarios.put("18. Seed block model name 'seed var [ ... ]'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto }\nseed var [ { id: 1 } ]");
        scenarios.put("19. Seed value seed keyword reference 'seed var[0]'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n ref: Long }\nseed Item [ { ref: seed var[0] } ]");

        // Job Scenarios
        scenarios.put("20. Job definition name 'job var { ... }'",
                "config { app: \"AdvApp\" }\njob var { cron: \"0 0 * * *\" }");
        scenarios.put("21. Job body property 'var: \"val\"'",
                "config { app: \"AdvApp\" }\njob myJob { var: \"val\", cron: \"0 0 * * *\" }");
        scenarios.put("22. Job action block with create",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n var: String }\njob myJob { cron: \"0 0 * * *\", action: { create Item { var = \"val\" } } }");

        // CRUD Scenarios
        scenarios.put("23. Crud property 'var: true'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n crud { var: true } }");
        scenarios.put("24. Crud search list '[var, name]'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n var: String \n crud { search: [var, id] } }");
        scenarios.put("25. Crud filter list '[var]'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n var: String \n crud { filter: [var] } }");
        scenarios.put("26. Crud sort list '[var]'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n var: String \n crud { sort: [var] } }");

        // Custom Endpoint Scenarios
        scenarios.put("27. Endpoint body type '(body: var)' with quoted path",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n endpoints { post \"/test\" (body: var) { } } }");
        scenarios.put("28. Endpoint return type '-> var'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n endpoints { get /test -> var { } } }");
        scenarios.put("29. Endpoint return generic type '-> List<var>'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n endpoints { get /test -> List<var> { } } }");
        scenarios.put("30. Endpoint property 'var: \"val\"'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n endpoints { get /test { var: \"val\" } } }");
        scenarios.put("31. Endpoint params block '{ var: String }'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n endpoints { get /test { params: { var: String } } } }");

        // TypeRef and Relation Target Scenarios
        scenarios.put("32. Field type 'var'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n f: var }");
        scenarios.put("33. Field generic type 'List<var>'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n f: List<var> }");
        scenarios.put("34. Relation target model 'cat: var @manyToOne'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n relations { cat: var @manyToOne } }");
        scenarios.put("35. Relation generic target 'cats: List<var> @oneToMany'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n relations { cats: List<var> @oneToMany } }");

        // Action Block and Guard Scenarios
        scenarios.put("36. Create action field named 'var = \"val\"'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n var: String \n endpoints { post /test { action: { create Item { var = \"val\" } } } } }");
        scenarios.put("37. Update action field named 'var = \"val\"'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n var: String \n endpoints { post /test { action: { update Item { var = \"val\" } } } } }");
        scenarios.put("38. Create action model named 'var'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n endpoints { post /test { action: { create var { id = 1 } } } } }");
        scenarios.put("39. Update action model named 'var'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n endpoints { post /test { action: { update var { id = 1 } } } } }");
        scenarios.put("40. Update action with 'on var'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n endpoints { post /test { action: { update Item on var { id = 1 } } } } }");
        scenarios.put("41. Guard with 'var' in condition",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n endpoints { get /test { guard: var > 0, \"var must be positive\" } } }");
        scenarios.put("42. Guard with 'entity.var' in condition",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n var: Int \n endpoints { get /test { guard: entity.var > 0, \"var must be positive\" } } }");
        scenarios.put("43. Field default value 'status: String = var'",
                "config { app: \"AdvApp\" }\nmodel Item { id: Long @id @auto \n status: String = var }");

        int total = scenarios.size();
        int passCount = 0;
        int failCount = 0;

        for (Map.Entry<String, String> entry : scenarios.entrySet()) {
            String name = entry.getKey();
            String code = entry.getValue();
            try {
                Lexer lexer = new Lexer(code);
                List<Token> tokens = lexer.tokenize();
                Parser parser = new Parser(tokens, "audited_test.zen");
                ZenithFile ast = parser.parse();
                if (ast != null) {
                    System.out.printf("[PASS] %-60s%n", name);
                    passCount++;
                } else {
                    System.out.printf("[FAIL - NULL AST] %-60s%n", name);
                    failCount++;
                }
            } catch (Exception e) {
                System.out.printf("[FAIL] %-60s -> %s%n", name, e.getMessage());
                failCount++;
            }
        }

        System.out.println("==================================================================");
        System.out.printf("Summary: %d scenarios tested: %d passed, %d failed%n",
                total, passCount, failCount);
        System.out.println("==================================================================");

        if (failCount > 0) {
            System.err.printf("[FATAL] %d of %d scenarios failed!%n", failCount, total);
            System.exit(1);
        } else {
            System.out.println(">> ALL 43 AUDITED PARSER SCENARIOS PASSED CLEANLY!");
            System.exit(0);
        }
    }
}
