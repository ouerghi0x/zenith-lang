package dev.zenith.test;

import dev.zenith.mcp.CompilerAdapter;
import dev.zenith.mcp.ErrorExplainer;
import dev.zenith.mcp.JsonValue;
import dev.zenith.mcp.ZenithMcpServer;
import dev.zenith.mcp.ZenithSpecRegistry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

/**
 * End-to-End Test Suite for Feature #24: Zenith MCP Server (Spec 24 / Task 25).
 * Validates protocol lifecycle, all 7 tools, resources, prompts, structured diagnostics,
 * and compiler code generation via MCP without crashing or hanging.
 */
public class Feature24McpServerTest {

    private static final ZenithMcpServer server = new ZenithMcpServer();

    // =========================================================================
    // Tier 1: Protocol Handshake & Discovery (Initialize, Tools, Resources, Prompts)
    // =========================================================================

    public static void T1_01_initialize_handshake_returns_protocol_and_capabilities() {
        String req = "{\"jsonrpc\":\"2.0\",\"id\":\"init-1\",\"method\":\"initialize\",\"params\":{}}";
        String resp = server.handleMessage(req);
        TestHarness.assertTrue(resp != null, "Response must not be null");
        TestHarness.assertTrue(resp.contains("\"protocolVersion\":\"2024-11-05\""), "Protocol version mismatch: " + resp);
        TestHarness.assertTrue(resp.contains("\"serverInfo\":{\"name\":\"zenith-mcp-server\""), "Server name mismatch: " + resp);
        TestHarness.assertTrue(resp.contains("\"tools\":{}"), "Must advertise tools capability");
        TestHarness.assertTrue(resp.contains("\"resources\":{}"), "Must advertise resources capability");
        TestHarness.assertTrue(resp.contains("\"prompts\":{}"), "Must advertise prompts capability");
    }

    public static void T1_02_tools_list_exposes_all_seven_spec_tools() {
        String req = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}";
        String resp = server.handleMessage(req);
        TestHarness.assertTrue(resp != null, "Response must not be null");
        TestHarness.assertTrue(resp.contains("zenith_get_specification"), "Missing zenith_get_specification tool");
        TestHarness.assertTrue(resp.contains("zenith_get_examples"), "Missing zenith_get_examples tool");
        TestHarness.assertTrue(resp.contains("zenith_list_features"), "Missing zenith_list_features tool");
        TestHarness.assertTrue(resp.contains("zenith_validate"), "Missing zenith_validate tool");
        TestHarness.assertTrue(resp.contains("zenith_explain_error"), "Missing zenith_explain_error tool");
        TestHarness.assertTrue(resp.contains("zenith_build"), "Missing zenith_build tool");
        TestHarness.assertTrue(resp.contains("zenith_get_project_info"), "Missing zenith_get_project_info tool");
    }

    public static void T1_03_resources_list_and_read_specification_and_examples() {
        String listReq = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"resources/list\"}";
        String listResp = server.handleMessage(listReq);
        TestHarness.assertTrue(listResp.contains("zenith://specification/syntax"), "Missing syntax resource");
        TestHarness.assertTrue(listResp.contains("zenith://specification/annotations"), "Missing annotations resource");
        TestHarness.assertTrue(listResp.contains("zenith://examples/basic-crud"), "Missing basic-crud example resource");

        String readReq = "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"resources/read\",\"params\":{\"uri\":\"zenith://specification/syntax\"}}";
        String readResp = server.handleMessage(readReq);
        TestHarness.assertTrue(readResp.contains("Zenith DSL Language Specification"), "Should return markdown specification content");
    }

    public static void T1_04_prompts_list_and_get_workflow_instructions() {
        String listReq = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"prompts/list\"}";
        String listResp = server.handleMessage(listReq);
        TestHarness.assertTrue(listResp.contains("create_crud_project"), "Missing create_crud_project prompt");
        TestHarness.assertTrue(listResp.contains("debug_zenith_project"), "Missing debug_zenith_project prompt");

        String getReq = "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"prompts/get\",\"params\":{\"name\":\"create_crud_project\"}}";
        String getResp = server.handleMessage(getReq);
        TestHarness.assertTrue(getResp.contains("Guide the AI coding agent"), "Prompt content missing in get prompt response");
    }

    // =========================================================================
    // Tier 2: Specification, Feature Discovery, & Examples Tool Calls
    // =========================================================================

    public static void T2_01_tool_call_get_specification_returns_full_grammar() {
        String req = "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"tools/call\",\"params\":{\"name\":\"zenith_get_specification\",\"arguments\":{}}}";
        String resp = server.handleMessage(req);
        TestHarness.assertTrue(resp.contains("Top-Level Declarations"), "Spec must detail top level constructs");
        TestHarness.assertTrue(resp.contains("@rateLimit"), "Spec must include rate limiting");
        TestHarness.assertTrue(resp.contains("send MailContract"), "Spec must include transactional emails");
    }

    public static void T2_02_tool_call_get_examples_topic_lookup() {
        String reqCrud = "{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"tools/call\",\"params\":{\"name\":\"zenith_get_examples\",\"arguments\":{\"topic\":\"basic-crud\"}}}";
        String respCrud = server.handleMessage(reqCrud);
        TestHarness.assertTrue(respCrud.contains("TaskManager") && respCrud.contains("pageable: true"), "Expected basic CRUD example");

        String reqEmail = "{\"jsonrpc\":\"2.0\",\"id\":12,\"method\":\"tools/call\",\"params\":{\"name\":\"zenith_get_examples\",\"arguments\":{\"topic\":\"emails\"}}}";
        String respEmail = server.handleMessage(reqEmail);
        TestHarness.assertTrue(respEmail.contains("mail OrderPlaced") && respEmail.contains("send OrderPlaced"), "Expected mail example");
    }

    public static void T2_03_tool_call_list_features_returns_structured_metadata() {
        String req = "{\"jsonrpc\":\"2.0\",\"id\":13,\"method\":\"tools/call\",\"params\":{\"name\":\"zenith_list_features\",\"arguments\":{}}}";
        String resp = server.handleMessage(req);
        TestHarness.assertTrue(resp.contains("rate_limit"), "Must list rate_limit feature: " + resp);
        TestHarness.assertTrue(resp.contains("mail"), "Must list mail feature: " + resp);
        TestHarness.assertTrue(resp.contains("indexes"), "Must list indexes feature: " + resp);
        TestHarness.assertTrue(resp.contains("actions"), "Must list actions feature: " + resp);
    }

    // =========================================================================
    // Tier 3: Validation, Diagnostics, & Error Explanation
    // =========================================================================

    public static void T3_01_tool_call_validate_valid_schema_returns_success() {
        String source = "config { app: \\\"QuickStore\\\" database: postgres } model Item { id: Long @id @auto name: String price: Decimal }";
        String req = "{\"jsonrpc\":\"2.0\",\"id\":20,\"method\":\"tools/call\",\"params\":{\"name\":\"zenith_validate\",\"arguments\":{\"source\":\"" + source + "\"}}}";
        String resp = server.handleMessage(req);
        TestHarness.assertTrue(resp.contains("success") && resp.contains("true"), "Valid source should return success:true: " + resp);
        TestHarness.assertTrue(resp.contains("diagnostics") && resp.contains("[]"), "Valid source diagnostics must be empty: " + resp);
    }

    public static void T3_02_tool_call_validate_syntax_error_returns_structured_diagnostic() {
        String source = "model Broken { id: Long @id @auto name: String";
        String req = "{\"jsonrpc\":\"2.0\",\"id\":21,\"method\":\"tools/call\",\"params\":{\"name\":\"zenith_validate\",\"arguments\":{\"source\":\"" + source + "\"}}}";
        String resp = server.handleMessage(req);
        TestHarness.assertTrue(resp.contains("success") && resp.contains("false"), "Invalid syntax must return success:false: " + resp);
        TestHarness.assertTrue(resp.contains("line"), "Must contain line number: " + resp);
        TestHarness.assertTrue(resp.contains("column"), "Must contain column number: " + resp);
        TestHarness.assertTrue(resp.contains("ZEN-401"), "Expected syntax error code ZEN-401: " + resp);
    }

    public static void T3_03_tool_call_explain_error_provides_actionable_fix() {
        String req = "{\"jsonrpc\":\"2.0\",\"id\":22,\"method\":\"tools/call\",\"params\":{\"name\":\"zenith_explain_error\",\"arguments\":{\"errorMessage\":\"roles: public is invalid\"}}}";
        String resp = server.handleMessage(req);
        TestHarness.assertTrue(resp.contains("ZEN-101"), "Expected ZEN-101 error code: " + resp);
        TestHarness.assertTrue(resp.contains("auth: false"), "Suggestion must mention using auth: false: " + resp);
    }

    // =========================================================================
    // Tier 4: Compilation via MCP (zenith_build) & Project Inspection
    // =========================================================================

    public static void T4_01_tool_call_build_generates_spring_boot_application() throws IOException {
        Path tempDir = Paths.get("target/mcp-test-build");
        if (Files.exists(tempDir)) {
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            }
        }

        String source = "config { app: \\\"McpApp\\\" package: \\\"com.mcp.test\\\" database: postgres } "
                + "model Customer { id: UUID @id @auto email: String @email @unique "
                + "endpoints { crud { pageable: true } } }";

        String req = "{\"jsonrpc\":\"2.0\",\"id\":30,\"method\":\"tools/call\",\"params\":{\"name\":\"zenith_build\",\"arguments\":{\"source\":\""
                + source + "\",\"outputDir\":\"target/mcp-test-build\"}}}";

        String resp = server.handleMessage(req);
        TestHarness.assertTrue(resp.contains("success") && resp.contains("true"), "zenith_build must return success:true: " + resp);
        TestHarness.assertTrue(resp.contains("pom.xml"), "Emitted files must include pom.xml: " + resp);

        // Verify generated file physically exists on disk
        Path pomPath = tempDir.resolve("pom.xml");
        TestHarness.assertTrue(Files.exists(pomPath), "pom.xml must physically exist at " + pomPath);
    }

    public static void T4_02_tool_call_get_project_info_inspects_models_and_config() {
        String source = "config { app: \\\"InfoApp\\\" database: postgres auth: jwt } "
                + "enum Role { ADMIN, USER } "
                + "model User { id: UUID @id @auto email: String @email password: String @password role: Role @role } "
                + "model Note { id: Long @id @auto content: String endpoints { crud { pageable: true } } }";

        String req = "{\"jsonrpc\":\"2.0\",\"id\":31,\"method\":\"tools/call\",\"params\":{\"name\":\"zenith_get_project_info\",\"arguments\":{\"source\":\"" + source + "\"}}}";
        String resp = server.handleMessage(req);
        TestHarness.assertTrue(resp.contains("valid") && resp.contains("true"), "Project should be valid: " + resp);
        TestHarness.assertTrue(resp.contains("InfoApp"), "Config app mismatch: " + resp);
        TestHarness.assertTrue(resp.contains("Note"), "Models must list Note: " + resp);
        TestHarness.assertTrue(resp.contains("hasCrud") && resp.contains("true"), "Note must have crud: true: " + resp);
    }

    public static void T4_03_resilience_to_malformed_requests() {
        // Invalid json
        String badJson = "{ invalid json here";
        String r1 = server.handleMessage(badJson);
        TestHarness.assertTrue(r1.contains("-32700"), "Must return JSON-RPC -32700 parse error");

        // Unknown method
        String badMethod = "{\"jsonrpc\":\"2.0\",\"id\":99,\"method\":\"unknown_method\"}";
        String r2 = server.handleMessage(badMethod);
        TestHarness.assertTrue(r2.contains("-32601"), "Must return -32601 method not found");
    }
}
