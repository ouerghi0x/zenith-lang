package dev.zenith.mcp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * High-performance, zero-dependency Model Context Protocol (MCP) Server for Zenith DSL.
 * Complies with MCP specification (JSON-RPC 2.0 over standard I/O).
 * Exposes 7 tools, 11+ resources, and 4 prompts for AI coding agents.
 */
public class ZenithMcpServer {

    public static final String PROTOCOL_VERSION = "2024-11-05";
    public static final String SERVER_NAME = "zenith-mcp-server";
    public static final String SERVER_VERSION = "1.0.0";

    private final InputStream in;
    private final PrintStream out;
    private volatile boolean running = true;

    public ZenithMcpServer() {
        this(System.in, System.out);
    }

    public ZenithMcpServer(InputStream in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    public static void main(String[] args) {
        // Run standard I/O server
        ZenithMcpServer server = new ZenithMcpServer();
        server.start();
    }

    public void start() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Handle optional Content-Length header framing if client uses HTTP/LSP style headers
                if (line.startsWith("Content-Length:") || line.startsWith("content-length:")) {
                    int colon = line.indexOf(':');
                    int length = Integer.parseInt(line.substring(colon + 1).trim());
                    // Skip empty line after header
                    reader.readLine();
                    char[] buf = new char[length];
                    int read = 0;
                    while (read < length) {
                        int r = reader.read(buf, read, length - read);
                        if (r == -1) break;
                        read += r;
                    }
                    String body = new String(buf, 0, read);
                    handleMessage(body);
                    continue;
                }

                handleMessage(line);
            }
        } catch (IOException e) {
            // Stream closed or error
        }
    }

    public void stop() {
        this.running = false;
    }

    /**
     * Processes a single JSON-RPC message and dispatches response if applicable.
     */
    public String handleMessage(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) return null;

        JsonValue req;
        try {
            req = JsonValue.parse(rawJson);
        } catch (Exception e) {
            return sendError(null, -32700, "Parse error: " + e.getMessage());
        }

        if (!req.isObject()) {
            return sendError(null, -32600, "Invalid Request: root must be an object");
        }

        JsonValue idVal = req.get("id");
        String method = req.getString("method", "");

        // Notifications (e.g. notifications/initialized) don't send responses
        if (idVal == null && method.startsWith("notifications/")) {
            return null;
        }

        try {
            switch (method) {
                case "initialize":
                    return handleInitialize(idVal, req.get("params"));
                case "notifications/initialized":
                    return null;
                case "ping":
                    return handlePing(idVal);
                case "tools/list":
                    return handleToolsList(idVal);
                case "tools/call":
                    return handleToolsCall(idVal, req.get("params"));
                case "resources/list":
                    return handleResourcesList(idVal);
                case "resources/read":
                    return handleResourcesRead(idVal, req.get("params"));
                case "prompts/list":
                    return handlePromptsList(idVal);
                case "prompts/get":
                    return handlePromptsGet(idVal, req.get("params"));
                default:
                    return sendError(idVal, -32601, "Method not found: " + method);
            }
        } catch (Exception ex) {
            return sendError(idVal, -32603, "Internal error: " + ex.getMessage());
        }
    }

    // =========================================================================
    // Protocol Handlers
    // =========================================================================

    private String handleInitialize(JsonValue id, JsonValue params) {
        JsonValue res = JsonValue.ofObject();
        res.put("protocolVersion", PROTOCOL_VERSION);

        JsonValue serverInfo = JsonValue.ofObject();
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        res.put("serverInfo", serverInfo);

        JsonValue capabilities = JsonValue.ofObject();
        capabilities.put("tools", JsonValue.ofObject());
        capabilities.put("resources", JsonValue.ofObject());
        capabilities.put("prompts", JsonValue.ofObject());
        res.put("capabilities", capabilities);

        return sendResult(id, res);
    }

    private String handlePing(JsonValue id) {
        return sendResult(id, JsonValue.ofObject());
    }

    private String handleToolsList(JsonValue id) {
        JsonValue res = JsonValue.ofObject();
        JsonValue tools = JsonValue.ofArray();

        // 1. zenith_get_specification
        tools.add(createToolDesc("zenith_get_specification",
                "Retrieve the Zenith language specification, primitives, syntax, and annotations.",
                createSchema(Collections.emptyMap(), Collections.emptyList())));

        // 2. zenith_get_examples
        Map<String, String> exProps = new LinkedHashMap<>();
        exProps.put("topic", "Topic/feature name (e.g. 'basic-crud', 'authentication', 'relationships', 'rate-limiting', 'emails')");
        tools.add(createToolDesc("zenith_get_examples",
                "Retrieve valid production .zen code examples for specific features.",
                createSchema(exProps, Collections.emptyList())));

        // 3. zenith_list_features
        tools.add(createToolDesc("zenith_list_features",
                "Discover supported Zenith compiler capabilities, decorators, and features.",
                createSchema(Collections.emptyMap(), Collections.emptyList())));

        // 4. zenith_validate
        Map<String, String> valProps = new LinkedHashMap<>();
        valProps.put("source", "Inline .zen source code to validate");
        valProps.put("filePath", "Optional file path of the .zen schema file");
        tools.add(createToolDesc("zenith_validate",
                "Validate Zenith source code or schema file and return structured machine-readable diagnostics.",
                createSchema(valProps, Collections.emptyList())));

        // 5. zenith_explain_error
        Map<String, String> expProps = new LinkedHashMap<>();
        expProps.put("errorCode", "Diagnostic code (e.g. ZEN-101, ZEN-201)");
        expProps.put("errorMessage", "Error message or snippet returned by compiler");
        tools.add(createToolDesc("zenith_explain_error",
                "Explain compiler errors and provide actionable code correction suggestions.",
                createSchema(expProps, Collections.singletonList("errorMessage"))));

        // 6. zenith_build
        Map<String, String> buildProps = new LinkedHashMap<>();
        buildProps.put("source", "Optional inline .zen source to compile");
        buildProps.put("filePath", "Path to root .zen file (default schema.zen)");
        buildProps.put("outputDir", "Target directory for generated Spring Boot 3 app (default generated-app)");
        tools.add(createToolDesc("zenith_build",
                "Compile a Zenith project and emit complete production Java 17 / Spring Boot 3 code.",
                createSchema(buildProps, Collections.emptyList())));

        // 7. zenith_get_project_info
        Map<String, String> infoProps = new LinkedHashMap<>();
        infoProps.put("source", "Optional inline .zen source");
        infoProps.put("filePath", "Path to .zen schema file");
        tools.add(createToolDesc("zenith_get_project_info",
                "Inspect project structure, models, fields, endpoints, enums, and enabled features.",
                createSchema(infoProps, Collections.emptyList())));

        res.put("tools", tools);
        return sendResult(id, res);
    }

    private String handleToolsCall(JsonValue id, JsonValue params) {
        if (params == null || !params.isObject()) {
            return sendError(id, -32602, "Missing params object for tool call");
        }

        String name = params.getString("name", "");
        JsonValue args = params.get("arguments");
        if (args == null || !args.isObject()) args = JsonValue.ofObject();

        JsonValue toolResult;
        switch (name) {
            case "zenith_get_specification":
                toolResult = executeGetSpecification();
                break;
            case "zenith_get_examples":
                toolResult = executeGetExamples(args.getString("topic", ""));
                break;
            case "zenith_list_features":
                toolResult = executeListFeatures();
                break;
            case "zenith_validate":
                toolResult = executeValidate(args.getString("source", null), args.getString("filePath", null));
                break;
            case "zenith_explain_error":
                toolResult = executeExplainError(args.getString("errorCode", ""), args.getString("errorMessage", ""));
                break;
            case "zenith_build":
                toolResult = executeBuild(args.getString("source", null), args.getString("filePath", null), args.getString("outputDir", null));
                break;
            case "zenith_get_project_info":
                toolResult = executeGetProjectInfo(args.getString("source", null), args.getString("filePath", null));
                break;
            default:
                return sendError(id, -32601, "Unknown tool: " + name);
        }

        return sendResult(id, toolResult);
    }

    private String handleResourcesList(JsonValue id) {
        JsonValue res = JsonValue.ofObject();
        JsonValue list = JsonValue.ofArray();

        Map<String, String> resources = ZenithSpecRegistry.getResources();
        for (String uri : resources.keySet()) {
            JsonValue item = JsonValue.ofObject();
            item.put("uri", uri);
            item.put("name", uri.replace("zenith://", "").replace('/', ' '));
            item.put("mimeType", uri.contains("examples") ? "text/x-zenith" : "text/markdown");
            list.add(item);
        }

        res.put("resources", list);
        return sendResult(id, res);
    }

    private String handleResourcesRead(JsonValue id, JsonValue params) {
        if (params == null || !params.isObject()) {
            return sendError(id, -32602, "Missing params object");
        }
        String uri = params.getString("uri", "");
        Map<String, String> resources = ZenithSpecRegistry.getResources();

        if (!resources.containsKey(uri)) {
            return sendError(id, -32602, "Resource not found: " + uri);
        }

        String content = resources.get(uri);
        JsonValue res = JsonValue.ofObject();
        JsonValue contents = JsonValue.ofArray();
        JsonValue c = JsonValue.ofObject();
        c.put("uri", uri);
        c.put("mimeType", uri.contains("examples") ? "text/x-zenith" : "text/markdown");
        c.put("text", content);
        contents.add(c);
        res.put("contents", contents);

        return sendResult(id, res);
    }

    private String handlePromptsList(JsonValue id) {
        JsonValue res = JsonValue.ofObject();
        JsonValue prompts = JsonValue.ofArray();

        Map<String, String> map = ZenithSpecRegistry.getPrompts();
        for (Map.Entry<String, String> e : map.entrySet()) {
            JsonValue p = JsonValue.ofObject();
            p.put("name", e.getKey());
            p.put("description", e.getValue().split("\n")[0]);
            prompts.add(p);
        }

        res.put("prompts", prompts);
        return sendResult(id, res);
    }

    private String handlePromptsGet(JsonValue id, JsonValue params) {
        if (params == null || !params.isObject()) {
            return sendError(id, -32602, "Missing params");
        }
        String name = params.getString("name", "");
        Map<String, String> map = ZenithSpecRegistry.getPrompts();
        if (!map.containsKey(name)) {
            return sendError(id, -32602, "Prompt not found: " + name);
        }

        JsonValue res = JsonValue.ofObject();
        res.put("description", "Workflow prompt: " + name);
        JsonValue msgs = JsonValue.ofArray();
        JsonValue m = JsonValue.ofObject();
        m.put("role", "user");
        JsonValue content = JsonValue.ofObject();
        content.put("type", "text");
        content.put("text", map.get(name));
        m.put("content", content);
        msgs.add(m);
        res.put("messages", msgs);

        return sendResult(id, res);
    }

    // =========================================================================
    // Tool Executors (Wrapping in MCP content format)
    // =========================================================================

    private JsonValue executeGetSpecification() {
        return wrapTextContent(ZenithSpecRegistry.getFullSpecification());
    }

    private JsonValue executeGetExamples(String topic) {
        Map<String, String> map = ZenithSpecRegistry.getExamples();
        if (topic != null && !topic.trim().isEmpty()) {
            String key = topic.trim().toLowerCase();
            if (map.containsKey(key)) {
                return wrapTextContent("// Example: " + key + "\n" + map.get(key));
            }
            // Partial match
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (e.getKey().contains(key)) {
                    return wrapTextContent("// Example: " + e.getKey() + "\n" + e.getValue());
                }
            }
        }

        // Return all examples combined
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : map.entrySet()) {
            sb.append("// ==========================================\n");
            sb.append("// Example: ").append(e.getKey()).append("\n");
            sb.append("// ==========================================\n");
            sb.append(e.getValue()).append("\n\n");
        }
        return wrapTextContent(sb.toString());
    }

    private JsonValue executeListFeatures() {
        List<ZenithSpecRegistry.FeatureInfo> features = ZenithSpecRegistry.getFeatures();
        JsonValue arr = JsonValue.ofArray();
        for (ZenithSpecRegistry.FeatureInfo f : features) {
            JsonValue o = JsonValue.ofObject();
            o.put("id", f.id);
            o.put("name", f.name);
            o.put("category", f.category);
            o.put("description", f.description);
            o.put("syntaxExample", f.syntaxExample);
            arr.add(o);
        }
        return wrapTextContent(arr.toJson());
    }

    private JsonValue executeValidate(String source, String filePath) {
        CompilerAdapter.ValidationResult res = CompilerAdapter.validate(source, filePath);
        return wrapTextContent(res.toJson().toJson());
    }

    private JsonValue executeExplainError(String errorCode, String errorMessage) {
        ErrorExplainer.Explanation exp = ErrorExplainer.explain(errorCode, errorMessage);
        return wrapTextContent(exp.toJson().toJson());
    }

    private JsonValue executeBuild(String source, String filePath, String outputDir) {
        JsonValue res = CompilerAdapter.build(source, filePath, outputDir);
        return wrapTextContent(res.toJson());
    }

    private JsonValue executeGetProjectInfo(String source, String filePath) {
        JsonValue res = CompilerAdapter.getProjectInfo(source, filePath);
        return wrapTextContent(res.toJson());
    }

    private static JsonValue wrapTextContent(String text) {
        JsonValue res = JsonValue.ofObject();
        JsonValue content = JsonValue.ofArray();
        JsonValue textItem = JsonValue.ofObject();
        textItem.put("type", "text");
        textItem.put("text", text);
        content.add(textItem);
        res.put("content", content);
        return res;
    }

    private static JsonValue createToolDesc(String name, String description, JsonValue inputSchema) {
        JsonValue t = JsonValue.ofObject();
        t.put("name", name);
        t.put("description", description);
        t.put("inputSchema", inputSchema);
        return t;
    }

    private static JsonValue createSchema(Map<String, String> properties, List<String> required) {
        JsonValue schema = JsonValue.ofObject();
        schema.put("type", "object");
        JsonValue props = JsonValue.ofObject();
        for (Map.Entry<String, String> e : properties.entrySet()) {
            JsonValue p = JsonValue.ofObject();
            p.put("type", "string");
            p.put("description", e.getValue());
            props.put(e.getKey(), p);
        }
        schema.put("properties", props);
        if (!required.isEmpty()) {
            JsonValue req = JsonValue.ofArray();
            for (String r : required) req.add(r);
            schema.put("required", req);
        }
        return schema;
    }

    // =========================================================================
    // I/O Communication Helpers
    // =========================================================================

    private String sendResult(JsonValue id, JsonValue result) {
        JsonValue resp = JsonValue.ofObject();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id != null ? id : JsonValue.ofNull());
        resp.put("result", result != null ? result : JsonValue.ofObject());
        String str = resp.toJson();
        writeOutput(str);
        return str;
    }

    private String sendError(JsonValue id, int code, String message) {
        JsonValue resp = JsonValue.ofObject();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id != null ? id : JsonValue.ofNull());
        JsonValue err = JsonValue.ofObject();
        err.put("code", code);
        err.put("message", message);
        resp.put("error", err);
        String str = resp.toJson();
        writeOutput(str);
        return str;
    }

    private void writeOutput(String json) {
        if (out != null) {
            out.println(json);
            out.flush();
        }
    }
}

