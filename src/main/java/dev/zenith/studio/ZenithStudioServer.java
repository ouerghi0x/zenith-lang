package dev.zenith.studio;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.zenith.analyzer.SemanticAnalyzer;
import dev.zenith.ast.EnumDef;
import dev.zenith.ast.FieldDef;
import dev.zenith.ast.JobDef;
import dev.zenith.ast.ModelDef;
import dev.zenith.ast.ZenithFile;
import dev.zenith.diagnostics.CompilerDiagnostic;
import dev.zenith.diagnostics.MavenDiagnosticParser;
import dev.zenith.generator.JavaSourceFile;
import dev.zenith.generator.trace.GenerationManifest;
import dev.zenith.generator.ProjectGenerator;
import dev.zenith.generator.TypeHelper;

import dev.zenith.parser.SchemaResolver;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Built-in zero-dependency HTTP server that powers Zenith Studio.
 * Serves the Studio IDE, performs live compilation, manages the Spring Boot
 * process,
 * and handles live project exports.
 */
public class ZenithStudioServer {

    private final int port;
    private final Path schemaPath;
    private HttpServer server;

    private Process runningSpringProcess = null;
    private final List<String> processLogs = new CopyOnWriteArrayList<>();
    private final List<CompilerDiagnostic> activeDiagnostics = new CopyOnWriteArrayList<>();
    private ZenithFile latestCompiledFile = null;

    // Interactive Test Runner State (Spec #04 & Spec #27)
    private Process runningTestProcess = null;
    private final List<String> testLogs = new CopyOnWriteArrayList<>();
    private SurefireReportParser.TestRunSummary latestTestSummary = new SurefireReportParser.TestRunSummary();

    // Active Docker Process Tracker (for continuous streaming)
    private Process activeDockerProcess = null;

    public ZenithStudioServer(int port, Path initialFilePath) {
        this.port = port;
        this.schemaPath = initialFilePath != null ? initialFilePath.toAbsolutePath().normalize()
                : Paths.get("schema.zen").toAbsolutePath().normalize();
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        // Routes
        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/edition", new EditionHandler());
        server.createContext("/api/compile", new CompileHandler());
        server.createContext("/api/run", new RunHandler());
        server.createContext("/api/stop", new StopHandler());
        server.createContext("/api/reset-db", new ResetDbHandler());
        server.createContext("/api/logs", new LogsHandler());
        server.createContext("/api/logs/clear", new ClearLogsHandler());
        server.createContext("/api/test/run", new TestRunHandler());
        server.createContext("/api/test/stop", new TestStopHandler());
        server.createContext("/api/test/results", new TestResultsHandler());
        server.createContext("/api/docker/status", new DockerStatusHandler());
        server.createContext("/api/docker/command", new DockerCommandHandler());
        server.createContext("/api/simulate/action", new SimulateActionHandler());
        server.createContext("/api/export", new ExportHandler());
        server.createContext("/api/schema", new SchemaHandler());
        server.createContext("/download/desktop", new DesktopDownloadHandler());

        server.start();

        // Optional Pro extension hooks
        if (dev.zenith.extension.ExtensionManager.getInstance().isProAvailable()) {
            dev.zenith.extension.ExtensionManager.getInstance().getProExtension().registerStudioRoutes(server);
        }

        String editionBadge = dev.zenith.extension.ExtensionManager.getInstance().isProLicensed()
                ? " [PRO ENTERPRISE - "
                        + dev.zenith.extension.ExtensionManager.getInstance().getProExtension().getLicenseHolder() + "]"
                : " [COMMUNITY EDITION]";

        System.out.println("\n  =======================================================");
        System.out.println("   ZENITH STUDIO IS LIVE AT: http://localhost:" + port + editionBadge);
        System.out.println("   Enterprise Visual Architecture & Full-Stack Engine");
        System.out.println("   Press Ctrl+C to stop the Studio.");
        System.out.println("  =======================================================\n");

        // Attempt to auto-open browser on host OS
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new URI("http://localhost:" + port));
            }
        } catch (Exception ignored) {
        }
    }

    // Static Resource Handler (index.html, studio.css, studio.js)
    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            String resourcePath = "/studio" + path;
            InputStream is = null;

            // Prioritize live disk files during local development, fallback to JAR
            // resources
            Path diskPath = Paths.get("src/main/resources/studio" + path);
            if (Files.exists(diskPath)) {
                is = Files.newInputStream(diskPath);
            } else {
                is = getClass().getResourceAsStream(resourcePath);
            }

            if (is == null) {
                sendResponse(exchange, 404, "text/plain", "File not found: " + path);
                return;
            }

            String contentType = "text/plain";
            if (path.endsWith(".html"))
                contentType = "text/html; charset=UTF-8";
            else if (path.endsWith(".css"))
                contentType = "text/css; charset=UTF-8";
            else if (path.endsWith(".js"))
                contentType = "application/javascript; charset=UTF-8";
            else if (path.endsWith(".png"))
                contentType = "image/png";
            else if (path.endsWith(".svg"))
                contentType = "image/svg+xml";
            else if (path.endsWith(".ico"))
                contentType = "image/x-icon";

            byte[] bytes = is.readAllBytes();
            is.close();
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            exchange.getResponseHeaders().set("Pragma", "no-cache");
            exchange.getResponseHeaders().set("Expires", "0");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    // Live In-Memory Compilation Handler
    private class CompileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String source = extractJsonField(body, "source");
            String currentFile = extractJsonField(body, "file");
            if (currentFile == null || currentFile.isBlank()) {
                currentFile = schemaPath.getFileName().toString();
            }

            Path baseDir = schemaPath != null && schemaPath.getParent() != null ? schemaPath.getParent()
                    : Paths.get(".");
            Path activeFilePath = baseDir.resolve(currentFile).normalize();

            try {
                SchemaResolver resolver = new SchemaResolver();
                resolver.setFileOverride(activeFilePath, source);

                ZenithFile ast;
                if (activeFilePath.equals(schemaPath.toAbsolutePath().normalize())) {
                    ast = resolver.resolveSource(source, activeFilePath);
                } else {
                    if (Files.exists(schemaPath)) {
                        ast = resolver.resolve(schemaPath);
                    } else {
                        ast = resolver.resolveSource(source, activeFilePath);
                    }
                }

                SemanticAnalyzer analyzer = new SemanticAnalyzer();
                ZenithFile analyzed = analyzer.analyze(ast);

                latestCompiledFile = analyzed;
                List<JavaSourceFile> generated = ProjectGenerator.generateAll(analyzed);

                // Build JSON response
                StringBuilder json = new StringBuilder();
                json.append("{\n");
                json.append("  \"success\": true,\n");
                json.append("  \"currentFile\": \"").append(escape(currentFile)).append("\",\n");
                json.append("  \"schemaFiles\": ").append(discoverZenFilesJson(baseDir, schemaPath)).append(",\n");
                json.append("  \"config\": {\n");
                json.append("    \"app\": \"").append(escape(analyzed.getConfig().getApp())).append("\",\n");
                json.append("    \"basePath\": \"").append(escape(analyzed.getConfig().getBasePath())).append("\",\n");
                json.append("    \"port\": ").append(analyzed.getConfig().getPort()).append(",\n");
                json.append("    \"hasAuth\": ").append(analyzed.hasAuth()).append(",\n");
                json.append("    \"hasStorage\": ").append(analyzed.hasStorage()).append(",\n");
                json.append("    \"hasRateLimiting\": ").append(analyzed.hasRateLimiting()).append(",\n");
                json.append("    \"rateLimit\": \"").append(escape(analyzed.getConfig().getRawRateLimit() != null ? analyzed.getConfig().getRawRateLimit() : "")).append("\",\n");
                json.append("    \"auth\": \"").append(escape(analyzed.getConfig().getString("auth", "none")))
                        .append("\"\n");
                json.append("  },\n");

                // Models
                json.append("  \"models\": [\n");
                for (int i = 0; i < analyzed.getModels().size(); i++) {
                    ModelDef m = analyzed.getModels().get(i);
                    if (i > 0)
                        json.append(",\n");
                    json.append("    {\n");
                    json.append("      \"name\": \"").append(escape(m.getName())).append("\",\n");
                    json.append("      \"fields\": [\n");
                    for (int j = 0; j < m.getFields().size(); j++) {
                        FieldDef f = m.getFields().get(j);
                        if (j > 0)
                            json.append(",\n");
                        json.append("        {\n");
                        json.append("          \"name\": \"").append(escape(f.getName())).append("\",\n");
                        json.append("          \"type\": \"").append(escape(f.getType().toString())).append("\",\n");
                        json.append("          \"id\": ").append(f.hasAnnotation("id")).append(",\n");
                        json.append("          \"auto\": ").append(
                                f.hasAnnotation("auto") || f.hasAnnotation("autoNow") || f.hasAnnotation("autoUpdate"))
                                .append(",\n");
                        json.append("          \"defaultValue\": \"")
                                .append(escape(f.getDefaultValue() != null ? f.getDefaultValue().toString() : ""))
                                .append("\",\n");
                        json.append("          \"unique\": ").append(f.hasAnnotation("unique")).append(",\n");
                        json.append("          \"password\": ").append(f.hasAnnotation("password")).append(",\n");
                        json.append("          \"file\": ").append(f.hasAnnotation("file")).append(",\n");
                        json.append("          \"image\": ").append(f.hasAnnotation("image")).append("\n");
                        json.append("        }");
                    }
                    json.append("\n      ],\n");

                    // Relations
                    json.append("      \"relations\": [\n");
                    for (int r = 0; r < m.getRelations().size(); r++) {
                        var rel = m.getRelations().get(r);
                        if (r > 0)
                            json.append(",\n");
                        json.append("        {\n");
                        json.append("          \"fieldName\": \"").append(escape(rel.getFieldName())).append("\",\n");
                        json.append("          \"targetModel\": \"").append(escape(rel.getTargetModel()))
                                .append("\",\n");
                        json.append("          \"relationType\": \"").append(escape(rel.getRelationType().name()))
                                .append("\"\n");
                        json.append("        }");
                    }
                    json.append("\n      ],\n");

                    // Custom endpoints
                    json.append("      \"hasCrud\": ").append(m.getCrudConfig() != null).append(",\n");
                    json.append("      \"crudRateLimit\": \"").append(escape(m.getCrudConfig() != null && m.getCrudConfig().hasRateLimit() ? m.getCrudConfig().getRateLimit().toDslString() : "")).append("\",\n");
                    json.append("      \"customEndpoints\": [\n");
                    for (int c = 0; c < m.getCustomEndpoints().size(); c++) {
                        var cep = m.getCustomEndpoints().get(c);
                        if (c > 0)
                            json.append(",\n");
                        json.append("        {\n");
                        json.append("          \"method\": \"").append(escape(cep.getMethod())).append("\",\n");
                        json.append("          \"path\": \"").append(escape(cep.getPath())).append("\",\n");
                        json.append("          \"bodyType\": \"")
                                .append(escape(cep.getBodyType() != null ? cep.getBodyType() : "")).append("\",\n");
                        json.append("          \"rateLimit\": \"")
                                .append(escape(cep.hasRateLimit() ? cep.getRateLimit().toDslString() : "")).append("\",\n");
                        json.append("          \"hasLogic\": ").append(cep.hasLogic()).append(",\n");
                        json.append("          \"description\": \"").append(escape(cep.getDescription()))
                                .append("\"\n");
                        json.append("        }");
                    }
                    json.append("\n      ]\n");
                    json.append("    }");
                }
                json.append("\n  ],\n");

                // DTOs
                json.append("  \"dtos\": [\n");
                for (int i = 0; i < analyzed.getDtos().size(); i++) {
                    var dto = analyzed.getDtos().get(i);
                    if (i > 0)
                        json.append(",\n");
                    json.append("    {\n");
                    json.append("      \"name\": \"").append(escape(dto.getName())).append("\",\n");
                    json.append("      \"fields\": [\n");
                    for (int f = 0; f < dto.getFields().size(); f++) {
                        FieldDef fd = dto.getFields().get(f);
                        if (f > 0)
                            json.append(",\n");
                        json.append("        {\"name\": \"").append(escape(fd.getName()))
                                .append("\", \"type\": \"").append(escape(fd.getType().getName()))
                                .append("\", \"optional\": ").append(fd.isOptional()).append("}");
                    }
                    json.append("\n      ]\n");
                    json.append("    }");
                }
                json.append("\n  ],\n");

                // Enums
                json.append("  \"enums\": [\n");
                for (int i = 0; i < analyzed.getEnums().size(); i++) {
                    EnumDef e = analyzed.getEnums().get(i);
                    if (i > 0)
                        json.append(",\n");
                    json.append("    {\n");
                    json.append("      \"name\": \"").append(escape(e.getName())).append("\",\n");
                    json.append("      \"values\": [");
                    for (int v = 0; v < e.getValues().size(); v++) {
                        if (v > 0)
                            json.append(", ");
                        json.append("\"").append(escape(e.getValues().get(v))).append("\"");
                    }
                    json.append("]\n");
                    json.append("    }");
                }
                json.append("\n  ],\n");

                // Auth User Model
                ModelDef authUserModel = analyzed.getUserModel();
                if (authUserModel != null) {
                    json.append("  \"userModel\": \"").append(escape(authUserModel.getName())).append("\",\n");
                }

                // Background Jobs
                json.append("  \"jobs\": [\n");
                for (int i = 0; i < analyzed.getJobs().size(); i++) {
                    JobDef j = analyzed.getJobs().get(i);
                    if (i > 0)
                        json.append(",\n");
                    json.append("    {\n");
                    json.append("      \"name\": \"").append(escape(j.getName())).append("\",\n");
                    json.append("      \"cron\": \"")
                            .append(escape(j.getCronExpression() != null ? j.getCronExpression() : "")).append("\",\n");
                    json.append("      \"fixedRate\": \"")
                            .append(escape(j.getFixedRate() != null ? j.getFixedRate() : "")).append("\",\n");
                    json.append("      \"description\": \"")
                            .append(escape(j.getDescription() != null ? j.getDescription() : "")).append("\"\n");
                    json.append("    }");
                }
                json.append("\n  ],\n");

                // Sockets (Spec #18 Real-Time WebSockets)
                json.append("  \"hasSockets\": ").append(analyzed.hasSockets()).append(",\n");
                json.append("  \"sockets\": [\n");
                for (int i = 0; i < analyzed.getSockets().size(); i++) {
                    var s = analyzed.getSockets().get(i);
                    if (i > 0)
                        json.append(",\n");
                    json.append("    {\n");
                    json.append("      \"name\": \"").append(escape(s.getName())).append("\",\n");
                    json.append("      \"endpoint\": \"").append(escape(s.getEndpoint())).append("\",\n");
                    json.append("      \"topic\": \"").append(escape(s.getTopic())).append("\",\n");
                    json.append("      \"broker\": \"").append(escape(s.getBroker())).append("\"\n");
                    json.append("    }");
                }
                json.append("\n  ],\n");

                // Generated Files
                json.append("  \"files\": [\n");
                for (int i = 0; i < generated.size(); i++) {
                    JavaSourceFile file = generated.get(i);
                    if (i > 0)
                        json.append(",\n");
                    json.append("    {\n");
                    json.append("      \"path\": \"").append(escape(file.getRelativePath())).append("\",\n");
                    json.append("      \"content\": \"").append(escape(file.getContent())).append("\"\n");
                    json.append("    }");
                }
                json.append("\n  ]\n");
                json.append("}");

                sendResponse(exchange, 200, "application/json", json.toString());

            } catch (Exception e) {
                int errLine = 1;
                int errCol = 1;
                String errFile = null;
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();

                if (e instanceof dev.zenith.parser.ParseException) {
                    dev.zenith.parser.ParseException pe = (dev.zenith.parser.ParseException) e;
                    errLine = pe.getLine();
                    errCol = pe.getColumn();
                    errFile = pe.getSourceFile();
                } else if (e instanceof dev.zenith.analyzer.AnalysisException) {
                    dev.zenith.analyzer.AnalysisException ae = (dev.zenith.analyzer.AnalysisException) e;
                    errLine = ae.getLine();
                    errCol = ae.getColumn();
                    errFile = ae.getSourceFile();
                } else if (msg.contains("Lexical error at ")) {
                    try {
                        String loc = msg.substring(msg.indexOf("at ") + 3);
                        loc = loc.substring(0, loc.indexOf(':'));
                        errLine = Integer.parseInt(loc);
                    } catch (Exception ignored) {
                    }
                }

                if (errFile == null && msg.startsWith("[")) {
                    int colon = msg.indexOf(':');
                    int closeBracket = msg.indexOf(']');
                    if (closeBracket > 1) {
                        int end = colon > 0 && colon < closeBracket ? colon : closeBracket;
                        errFile = msg.substring(1, end).trim();
                    }
                }
                if (errFile == null) {
                    errFile = currentFile;
                }

                String errJson = "{\n" +
                        "  \"success\": false,\n" +
                        "  \"error\": \"" + escape(msg) + "\",\n" +
                        "  \"file\": \"" + escape(errFile) + "\",\n" +
                        "  \"line\": " + errLine + ",\n" +
                        "  \"column\": " + errCol + "\n" +
                        "}";
                sendResponse(exchange, 200, "application/json", errJson);
            }
        }
    }

    // Launch Spring Boot Process
    private class RunHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (runningSpringProcess != null && runningSpringProcess.isAlive()) {
                sendResponse(exchange, 200, "application/json",
                        "{\"success\": true, \"message\": \"Already running\"}");
                return;
            }

            processLogs.clear();
            activeDiagnostics.clear();
            processLogs.add("[Zenith Studio] Generating project files into target/studio-run...");

            try {
                // Terminate any leftover Spring Boot instance on configured port before
                // launching
                int appPort = (latestCompiledFile != null && latestCompiledFile.getConfig() != null)
                        ? latestCompiledFile.getConfig().getPort()
                        : 8080;
                killProcessOnPort(appPort);
                if (appPort != 8080) {
                    killProcessOnPort(8080);
                }

                if (latestCompiledFile == null) {
                    if (schemaPath != null && Files.exists(schemaPath)) {
                        latestCompiledFile = new SemanticAnalyzer().analyze(new SchemaResolver().resolve(schemaPath));
                    } else {
                        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        String source = extractJsonField(body, "source");
                        String currentFile = extractJsonField(body, "file");
                        if (source != null && !source.isEmpty()) {
                            SchemaResolver resolver = new SchemaResolver();
                            Path baseDir = schemaPath != null && schemaPath.getParent() != null ? schemaPath.getParent()
                                    : Paths.get(".");
                            if (currentFile != null && !currentFile.isBlank()) {
                                resolver.setFileOverride(baseDir.resolve(currentFile).normalize(), source);
                            }
                            latestCompiledFile = new SemanticAnalyzer()
                                    .analyze(resolver.resolveSource(source, baseDir));
                        }
                    }
                }
                Path runDir = Paths.get("target/studio-run");
                if (Files.exists(runDir.resolve("src"))) {
                    try (var stream = Files.walk(runDir.resolve("src"))) {
                        stream.sorted(Comparator.reverseOrder())
                                .map(Path::toFile)
                                .forEach(File::delete);
                    }
                }
                if (Files.exists(runDir.resolve("target/classes"))) {
                    try (var stream = Files.walk(runDir.resolve("target/classes"))) {
                        stream.sorted(Comparator.reverseOrder())
                                .map(Path::toFile)
                                .forEach(File::delete);
                    }
                }

                // Auto-fallback to embedded H2 if PostgreSQL is selected but not listening on
                // localhost:5432
                if ("postgres".equalsIgnoreCase(latestCompiledFile.getConfig().getDatabase())
                        || "postgresql".equalsIgnoreCase(latestCompiledFile.getConfig().getDatabase())) {
                    if (!isPortListening("localhost", 5432, 400)) {
                        processLogs.add(
                                "[Zenith Studio] Notice: PostgreSQL not detected on localhost:5432. Auto-switching to embedded H2 in-memory database for instant local execution!");
                        latestCompiledFile.getConfig().set("database", "h2");
                    }
                }

                ProjectGenerator.writeToDirectory(latestCompiledFile, runDir);

                boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
                String mavenCmd = resolveMavenCommand(isWindows);
                processLogs.add("[Zenith Studio] Using Maven executable: " + mavenCmd);

                ProcessBuilder pb;
                if (isWindows) {
                    pb = new ProcessBuilder("cmd.exe", "/c", mavenCmd + " spring-boot:run");
                } else {
                    pb = new ProcessBuilder("sh", "-c", mavenCmd + " spring-boot:run");
                }
                pb.directory(runDir.toFile());
                pb.redirectErrorStream(true);

                runningSpringProcess = pb.start();

                // Stream stdout/stderr in background thread
                new Thread(() -> {
                    List<JavaSourceFile> currentSources = ProjectGenerator.generateAll(latestCompiledFile);
                    GenerationManifest activeManifest = ProjectGenerator.buildManifest(latestCompiledFile, currentSources);
                    MavenDiagnosticParser diagnosticParser = new MavenDiagnosticParser(activeManifest);

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(runningSpringProcess.getInputStream()))) {
                        String line;
                        boolean schemaErrorDetected = false;
                        while ((line = reader.readLine()) != null) {
                            processLogs.add(line);

                            // Intercept Java/Maven Compiler Errors for Generated Code Traceability
                            CompilerDiagnostic diag = diagnosticParser.parseLine(line);
                            if (diag != null) {
                                activeDiagnostics.add(diag);
                                processLogs.add("");
                                processLogs.add("[Zenith Diagnostic] 🔍 ROOT-CAUSE DIAGNOSTIC REPORT:");
                                for (String diagLine : diag.toCliReport().split("\n")) {
                                    processLogs.add("  " + diagLine);
                                }
                                processLogs.add("");
                            }

                            // Intercept Hibernate / Flyway / JDBC constraint schema mismatch errors
                            // (ISSUE-004)
                            if (!schemaErrorDetected && (line.contains("SchemaManagementException")
                                    || line.contains("wrong column type encountered")
                                    || line.contains("FlywayValidateException")
                                    || line.contains("Migration checksum mismatch")
                                    || line.contains("JdbcSQLIntegrityConstraintViolationException")
                                    || line.contains("ConstraintViolationException")
                                    || line.contains("NULL not allowed for column")
                                    || line.contains("non permis pour la colonne"))) {
                                schemaErrorDetected = true;
                                processLogs.add("");
                                processLogs.add(
                                        "[Zenith Diagnostic] ⚠️ DATABASE INTEGRITY / SCHEMA MISMATCH DETECTED (ISSUE-004)");
                                processLogs.add(
                                        "[Zenith Diagnostic] A model field was changed or a required column was not populated in seeds/schema.");
                                processLogs.add(
                                        "[Zenith Diagnostic] Database failed on constraint or column type validation.");
                                processLogs.add(
                                        "[Zenith Diagnostic] Quick Fix: Click \"Reset Dev DB\" in the Studio toolbar or use 'mvn clean' to recreate fresh tables.");
                                processLogs.add("");
                            }

                            if (processLogs.size() > 1000) {
                                processLogs.remove(0);
                            }
                        }
                    } catch (IOException ignored) {
                    }
                    try {
                        int code = runningSpringProcess.waitFor();
                        if (code != 0) {
                            processLogs.add("[Zenith Studio] Process terminated with exit code " + code);
                            processLogs.add(
                                    "[Zenith Studio] Tip: Ensure 'mvn' (Apache Maven) is installed and in your PATH, or run 'mvn spring-boot:run' inside 'target/studio-run'.");
                        }
                    } catch (Exception ignored) {
                    }
                }).start();

                sendResponse(exchange, 200, "application/json", "{\"success\": true}");

            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json",
                        "{\"success\": false, \"error\": \"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    // Stop Spring Boot Process
    private class StopHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (runningSpringProcess != null) {
                runningSpringProcess.destroyForcibly();
                runningSpringProcess = null;
                processLogs.add("[Zenith Studio] Spring Boot application stopped.");
            }
            int appPort = (latestCompiledFile != null && latestCompiledFile.getConfig() != null)
                    ? latestCompiledFile.getConfig().getPort()
                    : 8080;
            killProcessOnPort(appPort);
            if (appPort != 8080) {
                killProcessOnPort(8080);
            }
            sendResponse(exchange, 200, "application/json", "{\"success\": true}");
        }
    }

    // Fetch Live Logs
    private class LogsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            boolean isRunning = runningSpringProcess != null && runningSpringProcess.isAlive();
            StringBuilder sb = new StringBuilder("{\"running\": ").append(isRunning);

            // Include first compiler diagnostic if one occurred
            if (!activeDiagnostics.isEmpty()) {
                CompilerDiagnostic d = activeDiagnostics.get(0);
                sb.append(", \"diagnostic\": ").append(d.toJson());
            }

            sb.append(", \"logs\": [");
            for (int i = 0; i < processLogs.size(); i++) {
                if (i > 0)
                    sb.append(",");
                sb.append("\"").append(escape(processLogs.get(i))).append("\"");
            }
            sb.append("]}");
            sendResponse(exchange, 200, "application/json", sb.toString());
        }
    }

    // Clear Logs
    private class ClearLogsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            processLogs.clear();
            activeDiagnostics.clear();
            sendResponse(exchange, 200, "application/json", "{\"success\": true}");
        }
    }

    // Interactive Test Runner - Trigger mvn test (Spec #04)
    private class TestRunHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (runningTestProcess != null && runningTestProcess.isAlive()) {
                sendResponse(exchange, 200, "application/json",
                        "{\"success\": true, \"message\": \"Test suite already running\"}");
                return;
            }

            testLogs.clear();
            latestTestSummary = new SurefireReportParser.TestRunSummary();
            latestTestSummary.running = true;
            latestTestSummary.status = "RUNNING";
            testLogs.add("[Zenith Test Runner] Initializing automated Maven test suite...");

            try {
                if (latestCompiledFile == null) {
                    if (schemaPath != null && Files.exists(schemaPath)) {
                        latestCompiledFile = new SemanticAnalyzer().analyze(new SchemaResolver().resolve(schemaPath));
                    }
                }

                Path runDir = Paths.get("target/studio-run");
                if (latestCompiledFile != null) {
                    ProjectGenerator.writeToDirectory(latestCompiledFile, runDir);
                }

                // Delete previous surefire reports before new test run
                Path surefireDir = runDir.resolve("target/surefire-reports");
                if (Files.exists(surefireDir)) {
                    try (var stream = Files.walk(surefireDir)) {
                        stream.sorted(Comparator.reverseOrder())
                                .map(Path::toFile)
                                .forEach(File::delete);
                    } catch (Exception ignored) {
                    }
                }

                boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
                String mavenCmd = resolveMavenCommand(isWindows);
                testLogs.add("[Zenith Test Runner] Using Maven executable: " + mavenCmd);

                ProcessBuilder pb;
                if (isWindows) {
                    pb = new ProcessBuilder("cmd.exe", "/c", mavenCmd + " test -B");
                } else {
                    pb = new ProcessBuilder("sh", "-c", mavenCmd + " test -B");
                }
                pb.directory(runDir.toFile());
                pb.redirectErrorStream(true);

                runningTestProcess = pb.start();

                // Background stream thread for test logs and report parsing
                new Thread(() -> {
                    long startTime = System.currentTimeMillis();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(runningTestProcess.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            testLogs.add(line);
                            if (testLogs.size() > 1000) {
                                testLogs.remove(0);
                            }
                        }
                    } catch (IOException ignored) {
                    }

                    try {
                        int exitCode = runningTestProcess.waitFor();
                        double elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0;
                        testLogs.add("[Zenith Test Runner] Test run completed in " + String.format(Locale.US, "%.2f", elapsedSec) + "s with exit code " + exitCode);

                        // Parse Surefire XML Reports and connect to GenerationManifest (Spec #27)
                        GenerationManifest manifest = null;
                        if (latestCompiledFile != null) {
                            List<JavaSourceFile> sources = ProjectGenerator.generateAll(latestCompiledFile);
                            manifest = ProjectGenerator.buildManifest(latestCompiledFile, sources);
                        } else {
                            Path manifestPath = runDir.resolve(".zenith/generation-manifest.json");
                            if (Files.exists(manifestPath)) {
                                try {
                                    // Fallback if cached on disk
                                } catch (Exception ignored) {}
                            }
                        }

                        latestTestSummary = SurefireReportParser.parseReports(runDir.resolve("target/surefire-reports").toFile(), manifest);
                        latestTestSummary.running = false;
                        latestTestSummary.totalDuration = elapsedSec;
                        latestTestSummary.rawLogs.addAll(testLogs);

                        if (latestTestSummary.totalTests == 0) {
                            // Maven might have failed during compilation or early build
                            latestTestSummary.status = exitCode == 0 ? "PASSED" : "ERROR";
                        } else {
                            latestTestSummary.status = latestTestSummary.failedTests > 0 ? "FAILED" : "PASSED";
                        }

                    } catch (Exception e) {
                        latestTestSummary.running = false;
                        latestTestSummary.status = "ERROR";
                        testLogs.add("[Zenith Test Runner] Exception: " + e.getMessage());
                    } finally {
                        runningTestProcess = null;
                    }
                }).start();

                sendResponse(exchange, 200, "application/json", "{\"success\": true, \"message\": \"Tests started\"}");

            } catch (Exception e) {
                if (latestTestSummary != null) {
                    latestTestSummary.running = false;
                    latestTestSummary.status = "ERROR";
                }
                runningTestProcess = null;
                sendResponse(exchange, 500, "application/json",
                        "{\"success\": false, \"error\": \"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    // Stop active test execution
    private class TestStopHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (runningTestProcess != null) {
                runningTestProcess.destroyForcibly();
                runningTestProcess = null;
                testLogs.add("[Zenith Test Runner] Test run aborted by user.");
            }
            if (latestTestSummary != null) {
                latestTestSummary.running = false;
                latestTestSummary.status = "STOPPED";
                latestTestSummary.rawLogs.clear();
                latestTestSummary.rawLogs.addAll(testLogs);
            }
            sendResponse(exchange, 200, "application/json", "{\"success\": true, \"message\": \"Tests stopped\"}");
        }
    }

    // Get parsed test results and live status
    private class TestResultsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (latestTestSummary == null) {
                latestTestSummary = new SurefireReportParser.TestRunSummary();
            }
            boolean isRunning = runningTestProcess != null && runningTestProcess.isAlive();
            latestTestSummary.running = isRunning;
            if (isRunning) {
                latestTestSummary.rawLogs.clear();
                latestTestSummary.rawLogs.addAll(testLogs);
            }
            sendResponse(exchange, 200, "application/json", latestTestSummary.toJson());
        }
    }

    // Docker Status Handler
    private class DockerStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            boolean installed = false;
            boolean daemonRunning = false;
            String dockerVersion = "Unknown";
            String composeVersion = "Unknown";

            try {
                Process p = new ProcessBuilder(isWindows ? "docker.exe" : "docker", "--version").start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line = r.readLine();
                    if (line != null) {
                        dockerVersion = line.trim();
                        installed = true;
                    }
                }
                p.waitFor();
            } catch (Exception ignored) {}

            if (installed) {
                try {
                    Process p = new ProcessBuilder(isWindows ? "docker.exe" : "docker", "compose", "version").start();
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                        String line = r.readLine();
                        if (line != null) composeVersion = line.trim();
                    }
                    p.waitFor();
                } catch (Exception ignored) {}

                try {
                    Process p = new ProcessBuilder(isWindows ? "docker.exe" : "docker", "info").start();
                    boolean finished = p.waitFor(3, TimeUnit.SECONDS);
                    if (finished && p.exitValue() == 0) {
                        daemonRunning = true;
                    }
                } catch (Exception ignored) {}
            }

            // Inspect running containers for active project if available
            Map<String, String> serviceStatus = new HashMap<>();
            if (daemonRunning) {
                try {
                    Path runDir = Paths.get("target/studio-run");
                    if (Files.exists(runDir.resolve("docker-compose.yml"))) {
                        ProcessBuilder pb = new ProcessBuilder(isWindows ? "docker.exe" : "docker", "compose", "ps", "--format", "{{.Service}}:{{.State}}");
                        pb.directory(runDir.toFile());
                        Process p = pb.start();
                        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                            String line;
                            while ((line = r.readLine()) != null) {
                                line = line.trim();
                                if (line.contains(":")) {
                                    String[] parts = line.split(":", 2);
                                    serviceStatus.put(parts[0].trim(), parts[1].trim());
                                }
                            }
                        }
                        p.waitFor(3, TimeUnit.SECONDS);
                    }
                } catch (Exception ignored) {}
            }

            StringBuilder sb = new StringBuilder("{");
            sb.append("\"installed\": ").append(installed).append(",");
            sb.append("\"daemonRunning\": ").append(daemonRunning).append(",");
            sb.append("\"dockerVersion\": \"").append(escape(dockerVersion)).append("\",");
            sb.append("\"composeVersion\": \"").append(escape(composeVersion)).append("\",");
            sb.append("\"serviceStates\": {");
            int i = 0;
            for (Map.Entry<String, String> e : serviceStatus.entrySet()) {
                if (i++ > 0) sb.append(",");
                sb.append("\"").append(escape(e.getKey())).append("\": \"").append(escape(e.getValue())).append("\"");
            }
            sb.append("}}");

            sendResponse(exchange, 200, "application/json", sb.toString());
        }
    }

    // Docker Command Handler (build, up, down, start, stop, rm, logs)
    private class DockerCommandHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"success\": false, \"error\": \"Method not allowed\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String action = extractJsonField(body, "action"); // 'build' | 'up' | 'down' | 'start' | 'stop' | 'rm' | 'logs'
            String service = extractJsonField(body, "service"); // optional

            if (action == null || action.isBlank()) {
                sendResponse(exchange, 400, "application/json", "{\"success\": false, \"error\": \"Missing action\"}");
                return;
            }

            // Ensure project files exist in target/studio-run
            Path runDir = Paths.get("target/studio-run");
            try {
                if (latestCompiledFile != null) {
                    ProjectGenerator.writeToDirectory(latestCompiledFile, runDir);
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"success\": false, \"error\": \"Failed to generate docker files: " + escape(e.getMessage()) + "\"}");
                return;
            }

            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            String dockerExe = isWindows ? "docker.exe" : "docker";

            List<String> cmd = new ArrayList<>();
            cmd.add(dockerExe);

            switch (action.toLowerCase()) {
                case "build":
                    cmd.add("build");
                    cmd.add("-t");
                    String appName = (latestCompiledFile != null && latestCompiledFile.getConfig() != null && latestCompiledFile.getConfig().getApp() != null)
                            ? latestCompiledFile.getConfig().getApp().toLowerCase().replaceAll("[^a-z0-9_-]", "")
                            : "zenith-app";
                    cmd.add(appName + ":latest");
                    cmd.add(".");
                    break;
                case "up":
                    cmd.add("compose");
                    cmd.add("up");
                    cmd.add("-d");
                    if (service != null && !service.isBlank()) {
                        cmd.add(service.trim());
                    }
                    break;
                case "down":
                    cmd.add("compose");
                    cmd.add("down");
                    break;
                case "start":
                    cmd.add("compose");
                    cmd.add("start");
                    if (service != null && !service.isBlank()) cmd.add(service.trim());
                    break;
                case "stop":
                    cmd.add("compose");
                    cmd.add("stop");
                    if (service != null && !service.isBlank()) cmd.add(service.trim());
                    break;
                case "rm":
                    cmd.add("compose");
                    cmd.add("rm");
                    cmd.add("-s");
                    cmd.add("-f");
                    if (service != null && !service.isBlank()) cmd.add(service.trim());
                    break;
                case "logs":
                    cmd.add("compose");
                    cmd.add("logs");
                    cmd.add("-f");
                    cmd.add("--tail=150");
                    if (service != null && !service.isBlank()) cmd.add(service.trim());
                    break;
                default:
                    sendResponse(exchange, 400, "application/json", "{\"success\": false, \"error\": \"Unsupported action: " + escape(action) + "\"}");
                    return;
            }

            // Terminate any previous active Docker process (e.g. prior log follow or build)
            if (activeDockerProcess != null && activeDockerProcess.isAlive()) {
                activeDockerProcess.destroyForcibly();
                activeDockerProcess = null;
            }

            // Route process output directly to Studio console
            String fullCommandStr = String.join(" ", cmd);
            processLogs.add("");
            processLogs.add("[Zenith Docker] ========================================================");
            processLogs.add("[Zenith Docker] Executing: " + fullCommandStr);
            processLogs.add("[Zenith Docker] ========================================================");

            // Run process asynchronously so HTTP call returns immediately
            new Thread(() -> {
                try {
                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.directory(runDir.toFile());
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    activeDockerProcess = p;

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            processLogs.add(line);
                        }
                    }

                    int exitCode = p.waitFor();
                    if (exitCode == 0) {
                        processLogs.add("[Zenith Docker] ✔ Command completed successfully (exit code 0).");
                    } else {
                        processLogs.add("[Zenith Docker] ✖ Command exited with code " + exitCode + ".");
                    }
                } catch (Exception err) {
                    processLogs.add("[Zenith Docker] ✖ Execution error: " + err.getMessage());
                } finally {
                    activeDockerProcess = null;
                }
            }).start();

            sendResponse(exchange, 200, "application/json", "{\"success\": true, \"command\": \"" + escape(fullCommandStr) + "\"}");
        }
    }

    // Edition Status (Community vs PRO)
    private class EditionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                dev.zenith.extension.ExtensionManager.getInstance().togglePro();
            }
            Map<String, Object> status = dev.zenith.extension.ExtensionManager.getInstance().getEditionStatus();
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"isPro\": ").append(status.get("isPro")).append(",");
            sb.append("\"edition\": \"").append(escape(String.valueOf(status.get("edition")))).append("\",");
            sb.append("\"editionName\": \"").append(escape(String.valueOf(status.get("editionName")))).append("\",");
            sb.append("\"licenseHolder\": ")
                    .append(status.get("licenseHolder") != null
                            ? "\"" + escape(String.valueOf(status.get("licenseHolder"))) + "\""
                            : "null")
                    .append(",");
            sb.append("\"licenseExpiry\": ")
                    .append(status.get("licenseExpiry") != null
                            ? "\"" + escape(String.valueOf(status.get("licenseExpiry"))) + "\""
                            : "null");
            sb.append("}");
            sendResponse(exchange, 200, "application/json", sb.toString());
        }
    }

    // Reset Dev Database (ISSUE-004)
    private class ResetDbHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // 1. Stop any currently running Spring Boot process
                if (runningSpringProcess != null) {
                    runningSpringProcess.destroyForcibly();
                    runningSpringProcess = null;
                }
                int appPort = (latestCompiledFile != null && latestCompiledFile.getConfig() != null)
                        ? latestCompiledFile.getConfig().getPort()
                        : 8080;
                killProcessOnPort(appPort);
                if (appPort != 8080) {
                    killProcessOnPort(8080);
                }

                // 2. Clean target/studio-run generated classes and local embedded database
                // files
                Path runDir = Paths.get("target/studio-run");
                int deletedCount = 0;
                if (Files.exists(runDir)) {
                    try (var stream = Files.walk(runDir)) {
                        List<Path> toDelete = stream
                                .filter(p -> {
                                    String fn = p.getFileName().toString().toLowerCase();
                                    return fn.endsWith(".mv.db") || fn.endsWith(".trace.db")
                                            || fn.endsWith(".h2.db") || fn.endsWith(".db")
                                            || p.startsWith(runDir.resolve("target"))
                                            || p.startsWith(runDir.resolve("src"));
                                })
                                .sorted(Comparator.reverseOrder())
                                .collect(Collectors.toList());

                        for (Path p : toDelete) {
                            try {
                                Files.deleteIfExists(p);
                                deletedCount++;
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }

                processLogs.add("[Zenith Studio] 🧹 Dev database & build caches cleaned successfully (" + deletedCount
                        + " files removed).");
                processLogs.add("[Zenith Studio] You can now safely click 'Run' to boot with fresh database tables.");
                sendResponse(exchange, 200, "application/json",
                        "{\"success\": true, \"deletedFiles\": " + deletedCount + "}");
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json",
                        "{\"success\": false, \"error\": \"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    // Export Project as ZIP
    private class ExportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (latestCompiledFile == null) {
                sendResponse(exchange, 400, "text/plain", "No schema compiled yet");
                return;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(baos);

            List<JavaSourceFile> files = ProjectGenerator.generateAll(latestCompiledFile);
            for (JavaSourceFile f : files) {
                ZipEntry entry = new ZipEntry(f.getRelativePath());
                zos.putNextEntry(entry);
                zos.write(f.getContent().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            zos.close();

            byte[] zipData = baos.toByteArray();
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=\"" + latestCompiledFile.getConfig().getApp() + "-zenith.zip\"");
            exchange.sendResponseHeaders(200, zipData.length);
            OutputStream os = exchange.getResponseBody();
            os.write(zipData);
            os.close();
        }
    }

    // Native Desktop Installer Download Handler
    private class DesktopDownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Path exePath = Paths.get("desktop/dist/Zenith-Studio-Setup.exe");
            if (Files.exists(exePath)) {
                exchange.getResponseHeaders().set("Content-Type", "application/vnd.microsoft.portable-executable");
                exchange.getResponseHeaders().set("Content-Disposition",
                        "attachment; filename=\"Zenith-Studio-Setup.exe\"");
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                long size = Files.size(exePath);
                exchange.sendResponseHeaders(200, size);
                try (InputStream fis = Files.newInputStream(exePath);
                        OutputStream os = exchange.getResponseBody()) {
                    fis.transferTo(os);
                }
            } else {
                exchange.getResponseHeaders().set("Location",
                        "https://github.com/ouerghi0x/zenith-lang/releases/download/v1.0.0/Zenith-Studio-Setup.exe");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            }
        }
    }

    // Schema File Persistence Handler (reads/writes active schema file on disk)
    private class SchemaHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Path baseDir = schemaPath.getParent() != null ? schemaPath.getParent() : Paths.get(".");
            String query = exchange.getRequestURI().getQuery();
            String reqFile = null;
            if (query != null && query.contains("file=")) {
                for (String param : query.split("&")) {
                    if (param.startsWith("file=")) {
                        reqFile = java.net.URLDecoder.decode(param.substring(5), StandardCharsets.UTF_8);
                        break;
                    }
                }
            }

            Path targetPath = (reqFile != null && !reqFile.isBlank())
                    ? baseDir.resolve(reqFile).normalize()
                    : schemaPath.toAbsolutePath().normalize();

            String fileName = targetPath.getFileName().toString();

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                boolean exists = Files.exists(targetPath);
                String content = exists ? Files.readString(targetPath, StandardCharsets.UTF_8) : "";
                String filesJson = discoverZenFilesJson(baseDir, schemaPath);
                String resp = "{\n"
                        + "  \"exists\": " + exists + ",\n"
                        + "  \"fileName\": \"" + escape(fileName) + "\",\n"
                        + "  \"isRoot\": " + targetPath.equals(schemaPath.toAbsolutePath().normalize()) + ",\n"
                        + "  \"rootFile\": \"" + escape(schemaPath.getFileName().toString()) + "\",\n"
                        + "  \"files\": " + filesJson + ",\n"
                        + "  \"content\": \"" + escape(content) + "\"\n"
                        + "}";
                sendResponse(exchange, 200, "application/json", resp);
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String action = extractJsonField(body, "action");
                if ("create".equalsIgnoreCase(action)) {
                    String newFileName = extractJsonField(body, "fileName");
                    if (newFileName == null || newFileName.isBlank()) {
                        sendResponse(exchange, 400, "application/json", "{\"error\": \"Missing fileName\"}");
                        return;
                    }
                    if (!newFileName.endsWith(".zen")) {
                        newFileName = newFileName + ".zen";
                    }
                    Path newFilePath = baseDir.resolve(newFileName).normalize();
                    if (!Files.exists(newFilePath)) {
                        String starter = "// " + newFileName + "\n\nmodel "
                                + TypeHelper.capitalize(newFileName.replace(".zen", ""))
                                + " {\n    id: UUID @id @auto\n}\n";
                        Files.writeString(newFilePath, starter, StandardCharsets.UTF_8);
                    }
                    String filesJson = discoverZenFilesJson(baseDir, schemaPath);
                    sendResponse(exchange, 200, "application/json",
                            "{\"success\": true, \"fileName\": \"" + escape(newFileName) + "\", \"files\": " + filesJson
                                    + "}");
                    return;
                }

                String source = extractJsonField(body, "source");
                String fileParam = extractJsonField(body, "file");
                Path savePath = (fileParam != null && !fileParam.isBlank())
                        ? baseDir.resolve(fileParam).normalize()
                        : targetPath;

                if (source != null) {
                    if (savePath.getParent() != null && !Files.exists(savePath.getParent())) {
                        Files.createDirectories(savePath.getParent());
                    }
                    Files.writeString(savePath, source, StandardCharsets.UTF_8);
                    String filesJson = discoverZenFilesJson(baseDir, schemaPath);
                    sendResponse(exchange, 200, "application/json",
                            "{\"success\": true, \"fileName\": \"" + escape(savePath.getFileName().toString())
                                    + "\", \"files\": " + filesJson + "}");
                } else {
                    sendResponse(exchange, 400, "application/json", "{\"error\": \"Missing source\"}");
                }
            } else {
                sendResponse(exchange, 405, "application/json", "{\"error\": \"Method Not Allowed\"}");
            }
        }
    }

    private String discoverZenFilesJson(Path baseDir, Path rootPath) {
        List<Map<String, Object>> list = new ArrayList<>();
        Set<Path> seen = new HashSet<>();

        Path normRoot = rootPath.toAbsolutePath().normalize();
        seen.add(normRoot);

        Map<String, Object> rootMeta = new LinkedHashMap<>();
        rootMeta.put("name", normRoot.getFileName().toString());
        rootMeta.put("relativePath", baseDir.relativize(normRoot).toString().replace('\\', '/'));
        rootMeta.put("isRoot", true);
        rootMeta.put("isImported", true);
        list.add(rootMeta);

        try {
            SchemaResolver resolver = new SchemaResolver();
            resolver.resolve(normRoot);
            for (Path imp : resolver.getResolvedFiles()) {
                Path normImp = imp.toAbsolutePath().normalize();
                if (seen.add(normImp)) {
                    Map<String, Object> impMeta = new LinkedHashMap<>();
                    impMeta.put("name", normImp.getFileName().toString());
                    impMeta.put("relativePath", baseDir.relativize(normImp).toString().replace('\\', '/'));
                    impMeta.put("isRoot", false);
                    impMeta.put("isImported", true);
                    list.add(impMeta);
                }
            }
        } catch (Exception ignored) {
        }

        try (var stream = Files.list(baseDir)) {
            stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".zen"))
                    .forEach(p -> {
                        Path norm = p.toAbsolutePath().normalize();
                        if (seen.add(norm)) {
                            Map<String, Object> otherMeta = new LinkedHashMap<>();
                            otherMeta.put("name", norm.getFileName().toString());
                            otherMeta.put("relativePath", baseDir.relativize(norm).toString().replace('\\', '/'));
                            otherMeta.put("isRoot", false);
                            otherMeta.put("isImported", false);
                            list.add(otherMeta);
                        }
                    });
        } catch (Exception ignored) {
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> m = list.get(i);
            if (i > 0)
                sb.append(", ");
            sb.append("{\"name\": \"").append(escape(m.get("name").toString()))
                    .append("\", \"relativePath\": \"").append(escape(m.get("relativePath").toString()))
                    .append("\", \"isRoot\": ").append(m.get("isRoot"))
                    .append(", \"isImported\": ").append(m.get("isImported"))
                    .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static void sendResponse(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Zenith-License");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static String extractJsonField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx == -1)
            return "";
        int colon = json.indexOf(':', idx);
        int quoteStart = json.indexOf('"', colon);
        if (quoteStart == -1)
            return "";

        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        for (int i = quoteStart + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                if (c == 'n')
                    sb.append('\n');
                else if (c == 'r')
                    sb.append('\r');
                else if (c == 't')
                    sb.append('\t');
                else if (c == 'u' && i + 4 < json.length()) {
                    try {
                        int code = Integer.parseInt(json.substring(i + 1, i + 5), 16);
                        sb.append((char) code);
                        i += 4;
                    } catch (Exception ignored) {
                        sb.append(c);
                    }
                } else
                    sb.append(c);
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String resolveMavenCommand(boolean isWindows) {
        // 1. Direct check for known portable Maven path
        Path directMvn = Paths.get("tools", "apache-maven-3.9.6", "bin", isWindows ? "mvn.cmd" : "mvn");
        if (Files.exists(directMvn)) {
            return directMvn.toAbsolutePath().normalize().toString();
        }

        // 2. Check for portable Maven bundled anywhere in tools/
        Path toolsDir = Paths.get("tools");
        if (Files.exists(toolsDir)) {
            try {
                Optional<Path> found = Files.walk(toolsDir, 5)
                        .filter(p -> {
                            String fn = p.getFileName().toString().toLowerCase();
                            return isWindows ? fn.equals("mvn.cmd") : fn.equals("mvn");
                        })
                        .findFirst();
                if (found.isPresent()) {
                    return found.get().toAbsolutePath().normalize().toString();
                }
            } catch (Exception ignored) {
            }
        }

        // Fallback to standard PATH binary
        return isWindows ? "mvn.cmd" : "mvn";
    }

    private static boolean isPortListening(String host, int port, int timeoutMs) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void killProcessOnPort(int port) {
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            if (isWindows) {
                Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                        "Get-NetTCPConnection -LocalPort " + port
                                + " -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }")
                        .start();
                p.waitFor();
            } else {
                Process p = new ProcessBuilder("sh", "-c", "lsof -ti:" + port + " | xargs kill -9").start();
                p.waitFor();
            }
        } catch (Exception ignored) {
        }
    }

    // Action Simulation Handler (dev.zenith.engine)
    private class SimulateActionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }

            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                // Parse incoming simulation JSON request
                String model = extractJsonStringField(body, "model");
                String endpoint = extractJsonStringField(body, "endpoint");
                String method = extractJsonStringField(body, "method");
                if (method == null || method.isEmpty()) {
                    method = "POST";
                    if (endpoint != null && endpoint.contains(" ")) {
                        String[] parts = endpoint.split(" ", 2);
                        method = parts[0];
                        endpoint = parts[1];
                    }
                }

                Map<String, Object> entityState = extractJsonObjectField(body, "entityState");
                Map<String, Object> requestBody = extractJsonObjectField(body, "requestBody");
                Map<String, Object> currentUser = extractJsonObjectField(body, "currentUser");

                if (latestCompiledFile == null) {
                    if (Files.exists(schemaPath)) {
                        String source = Files.readString(schemaPath);
                        SchemaResolver resolver = new SchemaResolver();
                        latestCompiledFile = new dev.zenith.analyzer.SemanticAnalyzer()
                                .analyze(resolver.resolveSource(source, schemaPath));
                    }
                }

                if (latestCompiledFile == null) {
                    sendResponse(exchange, 400, "application/json", "{\"success\":false,\"error\":\"No compiled schema available for simulation\"}");
                    return;
                }

                dev.zenith.engine.SimulationResult res = dev.zenith.engine.ZenithEngine.getInstance().simulate(
                        latestCompiledFile, model, endpoint, method, entityState, requestBody, currentUser
                );

                StringBuilder json = new StringBuilder();
                json.append("{");
                json.append("\"success\":").append(res.isSuccess()).append(",");
                json.append("\"status\":").append(res.getStatus()).append(",");
                json.append("\"message\":\"").append(escape(res.getMessage())).append("\",");
                json.append("\"executionTimeMs\":").append(res.getExecutionTimeMs()).append(",");

                // stateDiff
                json.append("\"stateDiff\":{");
                boolean first = true;
                for (Map.Entry<String, dev.zenith.engine.trace.StateDiff> d : res.getStateDiff().entrySet()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("\"").append(escape(d.getKey())).append("\":{");
                    json.append("\"before\":").append(formatJsonValue(d.getValue().getBeforeValue())).append(",");
                    json.append("\"after\":").append(formatJsonValue(d.getValue().getAfterValue()));
                    json.append("}");
                }
                json.append("},");

                // boundVariables
                json.append("\"boundVariables\":{");
                first = true;
                for (Map.Entry<String, Object> bv : res.getBoundVariables().entrySet()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("\"").append(escape(bv.getKey())).append("\":").append(formatJsonValue(bv.getValue()));
                }
                json.append("},");

                // finalEntityState
                json.append("\"finalEntityState\":{");
                first = true;
                for (Map.Entry<String, Object> fe : res.getFinalEntityState().entrySet()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("\"").append(escape(fe.getKey())).append("\":").append(formatJsonValue(fe.getValue()));
                }
                json.append("},");

                // dispatchedEffects
                json.append("\"dispatchedEffects\":[");
                first = true;
                for (dev.zenith.engine.trace.DispatchedEffect eff : res.getDispatchedEffects()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{");
                    json.append("\"type\":\"").append(escape(eff.getType())).append("\",");
                    json.append("\"contract\":\"").append(escape(eff.getContract())).append("\",");
                    json.append("\"arguments\":{");
                    boolean firstArg = true;
                    for (Map.Entry<String, Object> arg : eff.getArguments().entrySet()) {
                        if (!firstArg) json.append(",");
                        firstArg = false;
                        json.append("\"").append(escape(arg.getKey())).append("\":").append(formatJsonValue(arg.getValue()));
                    }
                    json.append("}}");
                }
                json.append("],");

                // trace
                json.append("\"trace\":[");
                first = true;
                for (String t : res.getTrace()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("\"").append(escape(t)).append("\"");
                }
                json.append("]");

                json.append("}");
                sendResponse(exchange, 200, "application/json", json.toString());

            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"success\":false,\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }

        private String extractJsonStringField(String json, String field) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"");
            java.util.regex.Matcher m = p.matcher(json);
            return m.find() ? m.group(1) : "";
        }

        private Map<String, Object> extractJsonObjectField(String json, String field) {
            Map<String, Object> map = new LinkedHashMap<>();
            int idx = json.indexOf("\"" + field + "\"");
            if (idx == -1) return map;
            int openBrace = json.indexOf('{', idx);
            if (openBrace == -1) return map;
            int closeBrace = findMatchingBrace(json, openBrace);
            if (closeBrace == -1) return map;
            String objStr = json.substring(openBrace + 1, closeBrace).trim();
            if (objStr.isEmpty()) return map;

            // Simple key-value parser for flat/semi-flat JSON objects
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"([^\"]+)\"\\s*:\\s*([^,{}]+|\"[^\"]*\")");
            java.util.regex.Matcher m = p.matcher(objStr);
            while (m.find()) {
                String k = m.group(1);
                String v = m.group(2).trim();
                if (v.startsWith("\"") && v.endsWith("\"")) {
                    map.put(k, v.substring(1, v.length() - 1));
                } else if ("true".equalsIgnoreCase(v)) {
                    map.put(k, Boolean.TRUE);
                } else if ("false".equalsIgnoreCase(v)) {
                    map.put(k, Boolean.FALSE);
                } else if ("null".equalsIgnoreCase(v)) {
                    map.put(k, null);
                } else {
                    try {
                        if (v.contains(".")) {
                            map.put(k, Double.parseDouble(v));
                        } else {
                            map.put(k, Long.parseLong(v));
                        }
                    } catch (Exception ex) {
                        map.put(k, v);
                    }
                }
            }
            return map;
        }

        private int findMatchingBrace(String s, int openIdx) {
            int depth = 0;
            boolean inQuote = false;
            for (int i = openIdx; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                    inQuote = !inQuote;
                } else if (!inQuote) {
                    if (c == '{') depth++;
                    else if (c == '}') {
                        depth--;
                        if (depth == 0) return i;
                    }
                }
            }
            return -1;
        }

        private String formatJsonValue(Object val) {
            if (val == null) return "null";
            if (val instanceof Number || val instanceof Boolean) return val.toString();
            if (val instanceof Map) {
                StringBuilder sb = new StringBuilder("{");
                boolean f = true;
                for (Map.Entry<?, ?> e : ((Map<?, ?>) val).entrySet()) {
                    if (!f) sb.append(",");
                    f = false;
                    sb.append("\"").append(escape(String.valueOf(e.getKey()))).append("\":")
                      .append(formatJsonValue(e.getValue()));
                }
                sb.append("}");
                return sb.toString();
            }
            if (val instanceof List) {
                StringBuilder sb = new StringBuilder("[");
                boolean f = true;
                for (Object item : ((List<?>) val)) {
                    if (!f) sb.append(",");
                    f = false;
                    sb.append(formatJsonValue(item));
                }
                sb.append("]");
                return sb.toString();
            }
            return "\"" + escape(val.toString()) + "\"";
        }
    }

    private static String escape(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
