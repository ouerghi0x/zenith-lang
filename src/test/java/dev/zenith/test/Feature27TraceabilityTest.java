package dev.zenith.test;

import dev.zenith.analyzer.SemanticAnalyzer;
import dev.zenith.ast.ModelDef;
import dev.zenith.ast.SourceLocation;
import dev.zenith.ast.ZenithFile;
import dev.zenith.diagnostics.CompilerDiagnostic;
import dev.zenith.diagnostics.DiagnosticClassifier;
import dev.zenith.diagnostics.MavenDiagnosticParser;
import dev.zenith.generator.JavaSourceFile;
import dev.zenith.generator.ProjectGenerator;
import dev.zenith.generator.trace.GenerationManifest;
import dev.zenith.generator.trace.GenerationMapping;
import dev.zenith.generator.trace.TraceableSourceWriter;
import dev.zenith.parser.SchemaResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Feature #27: Generated Code Traceability & Compiler Diagnostics
 * Comprehensive Multi-Tier E2E Test Suite.
 */
public class Feature27TraceabilityTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("  Zenith Feature #27: Generated Code Traceability & Compiler Diagnostics        ");
        System.out.println("================================================================================");

        runTier1();
        runTier2();
        runTier3();
        runTier4();

        System.out.println("\nFeature #27 Results: " + passed + " passed, " + failed + " failed.");
        if (failed > 0) {
            System.exit(1);
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void test(String name, ThrowingRunnable runnable) {
        long start = System.currentTimeMillis();
        try {
            runnable.run();
            long dur = System.currentTimeMillis() - start;
            System.out.println("  [PASS] " + name + " (" + dur + "ms)");
            passed++;
        } catch (Throwable t) {
            long dur = System.currentTimeMillis() - start;
            System.err.println("  [FAIL] " + name + " (" + dur + "ms): " + t.getMessage());
            t.printStackTrace(System.err);
            failed++;
        }
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError(msg + " Expected: " + expected + ", Got: " + actual);
        }
    }

    // -------------------------------------------------------------------------
    // Tier 1: Traceable Code Emitter, AST Source Locations & Manifest Serialization
    // -------------------------------------------------------------------------
    private static void runTier1() {
        System.out.println("\n--- Running Tier 1: Traceable Code Emitter, Line Tracking & Manifest Serialization ---");

        test("T1_01_traceable_writer_tracks_lines_across_newlines", () -> {
            TraceableSourceWriter writer = new TraceableSourceWriter("com/example/Test.java");
            assertEquals(1, writer.getCurrentLine(), "Initial line should be 1");
            writer.append("package com.example;\n\n");
            assertEquals(3, writer.getCurrentLine(), "Line count after newlines");
            writer.append("public class Test {\r\n");
            assertEquals(4, writer.getCurrentLine(), "Line count with windows newline");
            writer.append("}\n");
            assertEquals(5, writer.getCurrentLine(), "Final line count");
        });

        test("T1_02_traceable_writer_captures_caller_stack_frame", () -> {
            TraceableSourceWriter writer = new TraceableSourceWriter("com/example/Foo.java");
            SourceLocation origin = new SourceLocation("schema.zen", 12, 5);
            try (var scope = writer.track("myElement", origin, "model Foo")) {
                writer.append("public void doSomething() {\n");
                writer.append("    System.out.println(\"hello\");\n");
                writer.append("}\n");
            }
            List<GenerationMapping> mappings = writer.getMappings();
            assertEquals(1, mappings.size(), "Should record 1 mapping");
            GenerationMapping m = mappings.get(0);
            assertEquals("com/example/Foo.java", m.getGeneratedFile(), "Generated file");
            assertEquals("myElement", m.getGeneratedElement(), "Element name");
            assertEquals(1, m.getStartLine(), "Start line");
            assertEquals(4, m.getEndLine(), "End line");
            assertEquals(12, m.getSourceOrigin().getStartLine(), "Origin start line");
            assertEquals("model Foo", m.getSnippet(), "Snippet");
            assertTrue(m.getGeneratorClass().contains("Feature27TraceabilityTest"), "Caller generator class");
        });

        test("T1_03_generation_manifest_serialization_and_relative_paths", () -> {
            GenerationManifest manifest = new GenerationManifest("orders.zen");
            SourceLocation loc = new SourceLocation("orders.zen", 25, 4);
            manifest.addMapping(new GenerationMapping(
                    "src/main/java/com/example/OrderController.java",
                    "createEndpoint",
                    40, 55,
                    "dev.zenith.generator.ControllerGenerator",
                    "generate",
                    140,
                    loc,
                    "crud { paginate: true }"
            ));

            String json = manifest.toJson();
            assertTrue(json.contains("\"zenithVersion\": \"1.0.0\""), "Contains zenithVersion");
            assertTrue(json.contains("\"rootSchema\": \"orders.zen\""), "Contains rootSchema");
            assertTrue(json.contains("\"generatedFile\": \"src/main/java/com/example/OrderController.java\""), "Contains generatedFile");
            assertTrue(json.contains("\"dev.zenith.generator.ControllerGenerator\""), "Contains generator class");
            assertTrue(json.contains("\"line\": 25"), "Contains schema line");
        });

        test("T1_04_ast_nodes_capture_exact_source_location", () -> {
            String schema = "model Customer {\n" +
                    "    id: Long @id @auto\n" +
                    "    name: String\n" +
                    "    endpoints {\n" +
                    "        crud {\n" +
                    "            paginate: true\n" +
                    "        }\n" +
                    "    }\n" +
                    "}";
            SchemaResolver resolver = new SchemaResolver();
            ZenithFile file = new SemanticAnalyzer().analyze(resolver.resolveSource(schema, Path.of(".")));
            ModelDef customer = file.findModel("Customer");
            assertTrue(customer != null, "Customer model resolved");
            assertTrue(customer.getSourceLocation() != null, "Source location exists");
            assertEquals(1, customer.getSourceLocation().getStartLine(), "Customer model declared at line 1");
            assertTrue(customer.getCrudConfig().getSourceLocation() != null, "CrudConfig source location exists");
            assertEquals(5, customer.getCrudConfig().getSourceLocation().getStartLine(), "Crud declared at line 5");
        });
    }

    // -------------------------------------------------------------------------
    // Tier 2: Diagnostic Classifier & Regex Stream Parser
    // -------------------------------------------------------------------------
    private static void runTier2() {
        System.out.println("\n--- Running Tier 2: Diagnostic Classifier & Regex Stream Parser ---");

        test("T2_01_classify_generator_defect_vs_specification_defect", () -> {
            assertEquals(CompilerDiagnostic.ErrorCategory.GENERATOR_DEFECT,
                    DiagnosticClassifier.classify("package org.springframework.web.bind.annotation does not exist", null),
                    "Missing framework import -> GENERATOR_DEFECT");

            assertEquals(CompilerDiagnostic.ErrorCategory.SPECIFICATION_DEFECT,
                    DiagnosticClassifier.classify("incompatible types: java.util.UUID cannot be converted to java.lang.Long", null),
                    "Type mismatch -> SPECIFICATION_DEFECT");

            assertEquals(CompilerDiagnostic.ErrorCategory.ENVIRONMENT_DEFECT,
                    DiagnosticClassifier.classify("Fatal error compiling: invalid target release: 21", null),
                    "JDK release -> ENVIRONMENT_DEFECT");

            assertEquals(CompilerDiagnostic.ErrorCategory.EXTERNAL_DEPENDENCY,
                    DiagnosticClassifier.classify("Could not resolve dependencies for project com.example:app:jar:0.0.1", null),
                    "Dependency failure -> EXTERNAL_DEPENDENCY");
        });

        test("T2_02_parse_maven_javac_linux_error_format", () -> {
            GenerationManifest manifest = new GenerationManifest("schema.zen");
            manifest.addMapping(new GenerationMapping(
                    "src/main/java/com/example/TaskController.java",
                    "create",
                    40, 60,
                    "dev.zenith.generator.ControllerGenerator",
                    "generate",
                    142,
                    new SourceLocation("schema.zen", 21, 9),
                    "crud { paginate: true }"
            ));

            MavenDiagnosticParser parser = new MavenDiagnosticParser(manifest);
            String mavenError = "[ERROR] /home/user/app/src/main/java/com/example/TaskController.java:[48,19] cannot find symbol: class CreateTaskRequest";
            CompilerDiagnostic diag = parser.parseLine(mavenError);

            assertTrue(diag != null, "Diagnostic parsed");
            assertEquals(48, diag.getGeneratedLine(), "Generated line");
            assertEquals(19, diag.getGeneratedColumn(), "Generated col");
            assertEquals("dev.zenith.generator.ControllerGenerator", diag.getGeneratorClass(), "Generator class");
            assertEquals("schema.zen", diag.getSourceOrigin().getSourceFile(), "Source schema");
            assertEquals(21, diag.getSourceOrigin().getStartLine(), "Source line");
            assertEquals(CompilerDiagnostic.ErrorCategory.SPECIFICATION_DEFECT, diag.getCategory(), "Error category");
        });

        test("T2_03_parse_maven_javac_windows_error_format", () -> {
            GenerationManifest manifest = new GenerationManifest("store.zen");
            manifest.addMapping(new GenerationMapping(
                    "src/main/java/com/example/OrderServiceImpl.java",
                    "pay",
                    100, 130,
                    "dev.zenith.generator.ServiceGenerator",
                    "generateImplementation",
                    450,
                    new SourceLocation("store.zen", 88, 5),
                    "status = OrderStatus.PAID"
            ));

            MavenDiagnosticParser parser = new MavenDiagnosticParser(manifest);
            String windowsError = "[ERROR] C:\\Users\\dev\\project\\src\\main\\java\\com\\example\\OrderServiceImpl.java:[114,37] cannot find symbol: method getPAID()";
            CompilerDiagnostic diag = parser.parseLine(windowsError);

            assertTrue(diag != null, "Windows diagnostic parsed");
            assertEquals(114, diag.getGeneratedLine(), "Generated line");
            assertEquals(37, diag.getGeneratedColumn(), "Generated column");
            assertEquals("dev.zenith.generator.ServiceGenerator", diag.getGeneratorClass(), "Generator class");
            assertEquals("store.zen", diag.getSourceOrigin().getSourceFile(), "Source schema");
            assertEquals(88, diag.getSourceOrigin().getStartLine(), "Source line");
            assertTrue(diag.toCliReport().contains("Generated Target:"), "CLI report formatting");
        });
    }

    // -------------------------------------------------------------------------
    // Tier 3: Core Generator Traceability (Controller, Service, Entity, Flyway)
    // -------------------------------------------------------------------------
    private static void runTier3() {
        System.out.println("\n--- Running Tier 3: Core Generator Traceability Output & File Manifest ---");

        test("T3_01_generators_produce_generation_mappings_and_clean_code", () -> {
            String schema = "config {\n" +
                    "    app: \"TraceApp\"\n" +
                    "    package: \"com.trace.app\"\n" +
                    "}\n" +
                    "model Invoice {\n" +
                    "    id: Long @id @auto\n" +
                    "    amount: Decimal(10, 2)\n" +
                    "    endpoints {\n" +
                    "        crud { paginate: true }\n" +
                    "    }\n" +
                    "}";
            SchemaResolver resolver = new SchemaResolver();
            ZenithFile file = new SemanticAnalyzer().analyze(resolver.resolveSource(schema, Path.of(".")));

            List<JavaSourceFile> generated = ProjectGenerator.generateAll(file);
            JavaSourceFile controllerFile = generated.stream()
                    .filter(f -> f.getRelativePath().endsWith("InvoiceController.java"))
                    .findFirst()
                    .orElse(null);

            assertTrue(controllerFile != null, "InvoiceController generated");
            assertFalse(controllerFile.getMappings().isEmpty(), "InvoiceController has mappings");
            GenerationMapping cm = controllerFile.getMappings().get(0);
            assertEquals("dev.zenith.generator.ControllerGenerator", cm.getGeneratorClass(), "Generator class");
            assertFalse(controllerFile.getContent().contains("/* @zenith-origin */"), "Generated java code remains 100% clean");

            JavaSourceFile serviceFile = generated.stream()
                    .filter(f -> f.getRelativePath().endsWith("InvoiceServiceImpl.java"))
                    .findFirst()
                    .orElse(null);
            assertTrue(serviceFile != null, "InvoiceServiceImpl generated");
            assertFalse(serviceFile.getMappings().isEmpty(), "InvoiceServiceImpl has mappings");
        });

        test("T3_02_writeToDirectory_emits_valid_generation_manifest_json", () -> {
            try {
                String schema = "config {\n" +
                        "    app: \"ManifestApp\"\n" +
                        "    package: \"com.manifest.app\"\n" +
                        "}\n" +
                        "model Item {\n" +
                        "    id: Long @id @auto\n" +
                        "    name: String\n" +
                        "}";
                SchemaResolver resolver = new SchemaResolver();
                ZenithFile file = new SemanticAnalyzer().analyze(resolver.resolveSource(schema, Path.of(".")));

                Path tempDir = Files.createTempDirectory("zenith-trace-test");
                try {
                    ProjectGenerator.writeToDirectory(file, tempDir);
                    Path manifestPath = tempDir.resolve(".zenith/generation-manifest.json");
                    assertTrue(Files.exists(manifestPath), ".zenith/generation-manifest.json written to disk");
                    String content = Files.readString(manifestPath);
                    assertTrue(content.contains("\"zenithVersion\": \"1.0.0\""), "Valid JSON manifest with zenithVersion");
                    assertTrue(content.contains("Item"), "Manifest contains Item mappings");
                } finally {
                    try (var s = Files.walk(tempDir)) {
                        s.sorted((a, b) -> b.compareTo(a)).map(Path::toFile).forEach(java.io.File::delete);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Tier 4: End-to-End Build Integration & Diagnostic Report Generation
    // -------------------------------------------------------------------------
    private static void runTier4() {
        System.out.println("\n--- Running Tier 4: End-to-End Diagnostic Synthesis ---");

        test("T4_01_full_diagnostic_synthesis_and_cli_output", () -> {
            String schema = "model Ticket {\n" +
                    "    id: Long @id @auto\n" +
                    "    subject: String\n" +
                    "    endpoints {\n" +
                    "        crud { paginate: true }\n" +
                    "    }\n" +
                    "}";
            SchemaResolver resolver = new SchemaResolver();
            ZenithFile file = new SemanticAnalyzer().analyze(resolver.resolveSource(schema, Path.of(".")));
            List<JavaSourceFile> generated = ProjectGenerator.generateAll(file);
            GenerationManifest manifest = ProjectGenerator.buildManifest(file, generated);

            MavenDiagnosticParser parser = new MavenDiagnosticParser(manifest);
            String simulatedError = "[ERROR] /app/src/main/java/com/example/controller/TicketController.java:[45,10] cannot find symbol: class TicketResponse";
            CompilerDiagnostic diag = parser.parseLine(simulatedError);

            assertTrue(diag != null, "Diagnostic synthesized");
            String report = diag.toCliReport();
            assertTrue(report.contains("[ZENITH COMPILER ERROR]"), "Contains header");
            assertTrue(report.contains("Generator Origin (Zenith Compiler Internals):"), "Contains generator origin");
            assertTrue(report.contains("dev.zenith.generator.ControllerGenerator"), "Attributes to ControllerGenerator");
            assertTrue(report.contains("Original Schema Source:"), "Contains original schema coordinates");
        });
    }

    private static void assertFalse(boolean cond, String msg) {
        if (cond) throw new AssertionError(msg);
    }
}

