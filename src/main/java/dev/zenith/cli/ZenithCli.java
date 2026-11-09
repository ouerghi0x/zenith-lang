package dev.zenith.cli;

import dev.zenith.analyzer.SemanticAnalyzer;
import dev.zenith.ast.ModelDef;
import dev.zenith.ast.ZenithFile;
import dev.zenith.generator.JavaSourceFile;
import dev.zenith.generator.ProjectGenerator;

import dev.zenith.parser.SchemaResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZenithCli {

    private static final String BANNER = "  ______            _ _   _     \n" +
            " |___  /           (_) | | |    \n" +
            "    / /  ___ _ __  _| |_| |__   \n" +
            "   / /  / _ \\ '_ \\| | __| '_ \\  \n" +
            "  / /__|  __/ | | | | |_| | | | \n" +
            " /_____|\\___|_| |_|_|\\__|_| |_| \n" +
            "  Next-Gen Web DSL for Java & Spring Boot\n";

    public static void main(String[] args) {
        if (args.length == 0) {
            printHelp();
            return;
        }

        String command = args[0].toLowerCase();
        try {
            switch (command) {
                case "build":
                    handleBuild(args);
                    break;
                case "validate":
                    handleValidate(args);
                    break;
                case "inspect":
                    handleInspect(args);
                    break;
                case "init":
                    handleInit(args);
                    break;
                case "watch":
                    handleWatch(args);
                    break;
                case "studio":
                    handleStudio(args);
                    break;
                case "doctor":
                    handleDoctor(args);
                    break;
                case "version":
                case "--version":
                case "-v":
                    String edition = dev.zenith.extension.ExtensionManager.getInstance().getEditionName();
                    System.out.println("Zenith DSL v1.0.0 [" + edition + "] (Java 17 / Spring Boot 3 target)");
                    break;
                case "help":
                case "--help":
                case "-h":
                default:
                    printHelp();
                    break;
            }
        } catch (Exception e) {
            System.err.println("\n[ERROR] " + e.getMessage());
            System.exit(1);
        }
    }

    private static void handleBuild(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: zenith build <file.zen> [-o <output-dir>]");
            System.exit(1);
        }

        String inputFilePath = args[1];
        String outputDir = "generated-app";

        for (int i = 2; i < args.length; i++) {
            if ("-o".equals(args[i]) || "--output".equals(args[i])) {
                if (i + 1 < args.length) {
                    outputDir = args[i + 1];
                    i++;
                }
            }
        }

        System.out.println(BANNER);
        System.out.println(">> Compiling: " + inputFilePath);
        long startTime = System.currentTimeMillis();

        ZenithFile file = parseAndAnalyze(inputFilePath);
        Path targetPath = Paths.get(outputDir);
        ProjectGenerator.writeToDirectory(file, targetPath);

        List<JavaSourceFile> generated = ProjectGenerator.generateAll(file);
        long elapsed = System.currentTimeMillis() - startTime;

        System.out.println(">> Successfully generated Spring Boot 3 application in " + elapsed + "ms!");
        System.out.println(">> Location: " + targetPath.toAbsolutePath().normalize());
        System.out.println("\n--- Generated Summary ---");
        System.out.println("  Models: " + file.getModels().size());
        for (ModelDef m : file.getModels()) {
            System.out.println("   * " + m.getName() + " (" + m.getFields().size() + " fields, "
                    + m.getRelations().size() + " relations)");
        }
        System.out.println("  Total Files Written: " + generated.size());
        System.out.println("\nTo run the generated application:");
        System.out.println("  cd " + outputDir);
        System.out.println("  mvn spring-boot:run\n");
    }

    private static void handleValidate(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: zenith validate <file.zen>");
            System.exit(1);
        }
        String inputFilePath = args[1];
        System.out.println(">> Validating " + inputFilePath + "...");
        ZenithFile file = parseAndAnalyze(inputFilePath);
        System.out.println(">> Schema is 100% valid!");
        System.out.println("   Found " + file.getModels().size() + " models and " + file.getEnums().size() + " enums.");
    }

    private static void handleInspect(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: zenith inspect <file.zen>");
            System.exit(1);
        }
        String inputFilePath = args[1];
        ZenithFile file = parseAndAnalyze(inputFilePath);

        System.out.println("==================================================");
        System.out.println(" ZENITH SCHEMA INSPECTION: " + file.getConfig().getApp());
        System.out.println(" Base Package : " + file.getConfig().getPackage());
        System.out.println(" Base Path    : " + file.getConfig().getBasePath());
        System.out.println(" Target Port  : " + file.getConfig().getPort());
        System.out.println(" Database     : " + file.getConfig().getDatabase());
        System.out.println("==================================================");

        for (ModelDef m : file.getModels()) {
            System.out.println("\n[Model] " + m.getName());
            System.out.println("  Fields:");
            for (var f : m.getFields()) {
                System.out.printf("    - %-15s : %-15s %s%n", f.getName(), f.getType(), f.getAnnotations());
            }
            if (!m.getRelations().isEmpty()) {
                System.out.println("  Relations:");
                for (var r : m.getRelations()) {
                    System.out.printf("    - %-15s -> %s (%s)%n", r.getFieldName(), r.getTargetModel(),
                            r.getRelationType());
                }
            }
            String resourcePath = file.getConfig().getBasePath() + "/" + dev.zenith.generator.TypeHelper
                    .toSnakeCase(dev.zenith.generator.TypeHelper.pluralize(m.getName()));
            System.out.println("  CRUD Endpoints:");
            System.out.println("    - GET    " + resourcePath);
            System.out.println("    - GET    " + resourcePath + "/{id}");
            System.out.println("    - POST   " + resourcePath);
            System.out.println("    - PUT    " + resourcePath + "/{id}");
            System.out.println("    - DELETE " + resourcePath + "/{id}");
            for (var c : m.getCustomEndpoints()) {
                System.out.println("    - " + String.format("%-7s", c.getMethod()) + resourcePath + c.getPath());
            }
        }
    }

    private static void handleInit(String[] args) throws IOException {
        String filename = "schema.zen";
        if (args.length > 1) {
            filename = args[1].endsWith(".zen") ? args[1] : args[1] + ".zen";
        }
        Path target = Paths.get(filename);
        if (Files.exists(target)) {
            System.err.println("File '" + filename + "' already exists.");
            return;
        }

        String template = "config {\n" +
                "    app: \"StoreApp\"\n" +
                "    package: \"com.example.store\"\n" +
                "    basePath: \"/api/v1\"\n" +
                "    port: 8080\n" +
                "    database: postgres\n" +
                "}\n\n" +
                "enum ProductStatus {\n" +
                "    DRAFT,\n" +
                "    PUBLISHED,\n" +
                "    ARCHIVED\n" +
                "}\n\n" +
                "model Category {\n" +
                "    id: Long @id @auto\n" +
                "    name: String(min = 2, max = 50) @unique\n" +
                "    description: String? @text\n" +
                "    createdAt: DateTime @autoNow\n\n" +
                "    relations {\n" +
                "        products: List<Product> @relation(oneToMany, mappedBy = \"category\")\n" +
                "    }\n" +
                "}\n\n" +
                "model Product {\n" +
                "    id: Long @id @auto\n" +
                "    sku: String(length = 20) @unique\n" +
                "    title: String(min = 3, max = 150)\n" +
                "    description: String? @text\n" +
                "    price: Decimal(precision = 10, scale = 2) @positive\n" +
                "    stock: Int @min(0) = 0\n" +
                "    status: ProductStatus = DRAFT\n" +
                "    createdAt: DateTime @autoNow\n" +
                "    updatedAt: DateTime @autoUpdate\n\n" +
                "    relations {\n" +
                "        category: Category @relation(manyToOne)\n" +
                "    }\n\n" +
                "    endpoints {\n" +
                "        crud {\n" +
                "            paginate: true\n" +
                "            search: [title, sku]\n" +
                "            filter: [status]\n" +
                "        }\n\n" +
                "        post \"/{id}/publish\" -> ProductResponse {\n" +
                "            description: \"Publishes a draft product\"\n" +
                "        }\n" +
                "    }\n" +
                "}\n";

        Files.writeString(target, template, StandardCharsets.UTF_8);
        System.out.println(">> Initialized new Zenith schema: " + filename);
        System.out.println(">> Run 'zenith build " + filename + "' to generate your Spring Boot project!");
    }

    private static ZenithFile parseAndAnalyze(String filePath) throws IOException {
        Path p = Paths.get(filePath);
        if (!Files.exists(p)) {
            throw new IllegalArgumentException("File not found: " + filePath);
        }
        SchemaResolver resolver = new SchemaResolver();
        ZenithFile ast = resolver.resolve(p);
        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        return analyzer.analyze(ast);
    }

    private static void handleWatch(String[] args) throws IOException, InterruptedException {
        if (args.length < 2) {
            System.err.println("Usage: zenith watch <file.zen> [-o <output-dir>]");
            System.exit(1);
        }

        String inputFilePath = args[1];
        String outputDir = "generated-app";
        for (int i = 2; i < args.length; i++) {
            if ("-o".equals(args[i]) || "--output".equals(args[i])) {
                if (i + 1 < args.length) {
                    outputDir = args[i + 1];
                    i++;
                }
            }
        }

        Path p = Paths.get(inputFilePath).toAbsolutePath().normalize();
        if (!Files.exists(p)) {
            System.err.println("File not found: " + inputFilePath);
            System.exit(1);
        }

        System.out.println(BANNER);
        System.out.println(">> Watching file: " + p);
        System.out.println(">> Target output: " + Paths.get(outputDir).toAbsolutePath().normalize());
        System.out.println(">> Press Ctrl+C to stop.\n");

        SchemaResolver resolver = new SchemaResolver();
        // Initial build
        doSilentBuild(inputFilePath, outputDir, resolver);

        Map<Path, Long> lastModifiedMap = new HashMap<>();
        updateWatchTimes(resolver, lastModifiedMap, p);

        while (true) {
            Thread.sleep(500);
            try {
                boolean changed = false;
                for (Map.Entry<Path, Long> entry : lastModifiedMap.entrySet()) {
                    Path file = entry.getKey();
                    if (Files.exists(file)) {
                        long current = Files.getLastModifiedTime(file).toMillis();
                        if (current > entry.getValue()) {
                            changed = true;
                            break;
                        }
                    }
                }

                if (changed) {
                    System.out.println("\n>> Schema change detected at "
                            + java.time.LocalTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                            + ". Recompiling...");
                    doSilentBuild(inputFilePath, outputDir, resolver);
                    updateWatchTimes(resolver, lastModifiedMap, p);
                }
            } catch (Exception e) {
                // Ignore transient write lock errors while editor is saving
            }
        }
    }

    private static void updateWatchTimes(SchemaResolver resolver, Map<Path, Long> map, Path rootPath) {
        map.clear();
        if (Files.exists(rootPath)) {
            try {
                map.put(rootPath, Files.getLastModifiedTime(rootPath).toMillis());
            } catch (IOException ignored) {
            }
        }
        for (Path f : resolver.getResolvedFiles()) {
            if (Files.exists(f)) {
                try {
                    map.put(f, Files.getLastModifiedTime(f).toMillis());
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void doSilentBuild(String inputFilePath, String outputDir, SchemaResolver resolver) {
        long start = System.currentTimeMillis();
        try {
            Path p = Paths.get(inputFilePath);
            ZenithFile ast = resolver.resolve(p);
            ZenithFile file = new SemanticAnalyzer().analyze(ast);
            Path targetPath = Paths.get(outputDir);
            ProjectGenerator.writeToDirectory(file, targetPath);
            List<JavaSourceFile> generated = ProjectGenerator.generateAll(file);
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("   [SUCCESS] Recompiled in " + elapsed + "ms -> " + generated.size()
                    + " files up-to-date (" + file.getModels().size() + " models, " + resolver.getResolvedFiles().size()
                    + " schemas)");
        } catch (Exception e) {
            System.err.println("   [BUILD FAILED] " + e.getMessage());
        }
    }

    private static void handleDoctor(String[] args) {
        System.out.println(BANNER);
        System.out.println("==================================================");
        System.out.println("     ZENITH SYSTEM & ENVIRONMENT DIAGNOSTICS");
        System.out.println("==================================================");

        int warnings = 0;

        // 1. Zenith Version & CLI
        System.out.println("[OK] Zenith CLI: v1.0.0 (Java 17 / Spring Boot 3 target)");

        // 2. OS & Architecture
        String osName = System.getProperty("os.name", "Unknown");
        String osArch = System.getProperty("os.arch", "Unknown");
        System.out.println("[OK] Operating System: " + osName + " (" + osArch + ")");

        // 3. Java Runtime
        String javaVersion = System.getProperty("java.version", "Unknown");
        String javaVendor = System.getProperty("java.vendor", "Unknown");
        int majorJava = parseJavaMajorVersion(javaVersion);
        if (majorJava >= 17) {
            System.out.println(
                    "[OK] Java Runtime: " + javaVendor + " " + javaVersion + " (Java " + majorJava + " >= 17)");
        } else {
            System.out.println(
                    "[WARNING] Java Runtime: " + javaVersion + " detected. Zenith recommends Java 17 or higher.");
            warnings++;
        }

        // 4. Java Development Kit (JDK / javac)
        String javacOutput = runCommandCapture("javac -version", "javac", "-version");
        if (javacOutput != null && javacOutput.toLowerCase().contains("javac")) {
            System.out.println("[OK] Java Development Kit (JDK): Available (" + javacOutput.trim() + ")");
        } else {
            System.out.println("[WARNING] JDK compiler ('javac') not found in PATH.");
            System.out.println("          Generated Spring Boot projects require a JDK to compile.");
            System.out.println("          Download JDK 17+: https://adoptium.net");
            warnings++;
        }

        // 5. Maven Build Tool
        boolean isWin = osName.toLowerCase().contains("win");
        String mvnPath = resolveMavenPath(isWin);
        if (mvnPath != null) {
            String mvnVer = runCommandCapture(mvnPath + " -v", mvnPath, "-v");
            String summary = mvnVer != null ? mvnVer.split("\\r?\\n")[0] : mvnPath;
            System.out.println("[OK] Maven Build Tool: Available (" + summary + ")");
        } else {
            System.out.println("[WARNING] Apache Maven ('mvn') not found in PATH or tools/ directory.");
            System.out.println("          Building generated Spring Boot applications requires Maven.");
            System.out.println(
                    "          Install via Scoop ('scoop install maven') or download: https://maven.apache.org");
            warnings++;
        }

        // 6. Docker Engine (Optional)
        String dockerVer = runCommandCapture("docker --version", "docker", "--version");
        if (dockerVer != null && dockerVer.toLowerCase().contains("docker")) {
            System.out
                    .println("[INFO] Docker Engine: Available (" + dockerVer.trim() + ") - Container features enabled");
        } else {
            System.out.println(
                    "[INFO] Docker Engine: Not found (Optional: needed only for containerized postgres/minio)");
        }

        // 7. Binary Location & PATH check
        try {
            java.security.CodeSource cs = ZenithCli.class.getProtectionDomain().getCodeSource();
            if (cs != null) {
                Path jarLoc = Paths.get(cs.getLocation().toURI()).toAbsolutePath().normalize();
                System.out.println("[OK] Installation Path: " + jarLoc);
            }
        } catch (Exception ignored) {
        }

        System.out.println("==================================================");
        if (warnings == 0) {
            System.out.println(
                    ">> Status: All essential dependencies are verified! You are ready to build with Zenith.\n");
        } else {
            System.out.println(">> Doctor completed with " + warnings + " recommendation(s).\n");
        }
    }

    private static int parseJavaMajorVersion(String version) {
        if (version == null)
            return 0;
        try {
            String[] parts = version.split("\\.");
            int first = Integer.parseInt(parts[0]);
            if (first == 1 && parts.length > 1) {
                return Integer.parseInt(parts[1]);
            }
            return first;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String resolveMavenPath(boolean isWindows) {
        Path directMvn = Paths.get("tools", "apache-maven-3.9.6", "bin", isWindows ? "mvn.cmd" : "mvn");
        if (Files.exists(directMvn)) {
            return directMvn.toAbsolutePath().normalize().toString();
        }
        Path toolsDir = Paths.get("tools");
        if (Files.exists(toolsDir)) {
            try {
                java.util.Optional<Path> found = Files.walk(toolsDir, 5)
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
        // Test PATH
        String test = runCommandCapture(isWindows ? "mvn.cmd -v" : "mvn -v", isWindows ? "mvn.cmd" : "mvn", "-v");
        if (test != null && test.toLowerCase().contains("apache maven")) {
            return isWindows ? "mvn.cmd" : "mvn";
        }
        return null;
    }

    private static String runCommandCapture(String displayCmd, String... commandArgs) {
        try {
            Process p = new ProcessBuilder(commandArgs).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            return p.exitValue() == 0 ? sb.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void handleStudio(String[] args) throws IOException {
        int port = 3000;
        Path schemaPath = null;
        boolean desktopMode = false;

        for (int i = 1; i < args.length; i++) {
            if ("-p".equals(args[i]) || "--port".equals(args[i])) {
                if (i + 1 < args.length) {
                    port = Integer.parseInt(args[i + 1]);
                    i++;
                }
            } else if ("--desktop".equals(args[i]) || "-d".equals(args[i])) {
                desktopMode = true;
            } else if (!args[i].startsWith("-") && schemaPath == null) {
                schemaPath = Paths.get(args[i]);
            }
        }

        System.out.println(BANNER);
        dev.zenith.studio.ZenithStudioServer server = new dev.zenith.studio.ZenithStudioServer(port, schemaPath);
        server.start();

        if (desktopMode) {
            System.out.println("   ? Desktop App Mode active: launching standalone native window...");
            // If not already scheduled by zenith.bat wrapper, launch directly from Java
            if (!"1".equals(System.getenv("ZENITH_DESKTOP_LAUNCHED"))) {
                launchDesktopApp("http://localhost:" + port);
            }
        }

        // Keep main thread alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException ignored) {
        }
    }

    private static void launchDesktopApp(String url) {
        Thread launcher = new Thread(() -> {
            try {
                Thread.sleep(600);
                String os = System.getProperty("os.name", "").toLowerCase();
                if (os.contains("win")) {
                    try {
                        new ProcessBuilder("cmd", "/c", "start", "msedge", "--app=" + url).start();
                        return;
                    } catch (Exception ignored) {
                    }
                    try {
                        new ProcessBuilder("cmd", "/c", "start", "chrome", "--app=" + url).start();
                        return;
                    } catch (Exception ignored) {
                    }
                }
                if (java.awt.Desktop.isDesktopSupported()
                        && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                }
            } catch (Exception e) {
                System.err.println("   [Desktop] Could not launch app window automatically: " + e.getMessage());
            }
        }, "zenith-desktop-launcher");
        launcher.setDaemon(true);
        launcher.start();
    }

    private static void printHelp() {
        System.out.println(BANNER);
        System.out.println("Zenith CLI - The high-level Web DSL targeting Spring Boot & Java 17\n");
        System.out.println("Usage:");
        System.out.println(
                "  zenith studio [file.zen] [-p <port>] [--desktop]  Launch Zenith Studio (add --desktop for native app window)");
        System.out.println("  zenith build <file.zen> [-o <out-dir>]    Compile schema to a full Spring Boot 3 app");
        System.out
                .println("  zenith watch <file.zen> [-o <out-dir>]    Watch schema file and auto-recompile on change");
        System.out.println("  zenith validate <file.zen>                Validate schema syntax and semantics");
        System.out.println("  zenith inspect <file.zen>                 Show summary of models and endpoints");
        System.out.println("  zenith init [file.zen]                    Create a starter schema file");
        System.out.println(
                "  zenith doctor                             Diagnose Java, Maven, Docker and system environment");
        System.out.println("  zenith version                            Display installed Zenith version");
        System.out.println("  zenith help                               Show this help message\n");
    }
}
