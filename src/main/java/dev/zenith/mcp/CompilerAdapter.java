package dev.zenith.mcp;

import dev.zenith.analyzer.AnalysisException;
import dev.zenith.analyzer.SemanticAnalyzer;
import dev.zenith.ast.*;
import dev.zenith.generator.JavaSourceFile;
import dev.zenith.generator.ProjectGenerator;
import dev.zenith.parser.ParseException;
import dev.zenith.parser.SchemaResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Clean Compiler Adapter for MCP tools.
 * Delegates directly to Parser, SchemaResolver, SemanticAnalyzer, and ProjectGenerator.
 * Does not re-implement any compiler logic.
 */
public class CompilerAdapter {

    public static class ValidationResult {
        public final boolean success;
        public final List<Diagnostic> diagnostics;
        public final ZenithFile ast;

        public ValidationResult(boolean success, List<Diagnostic> diagnostics, ZenithFile ast) {
            this.success = success;
            this.diagnostics = diagnostics != null ? diagnostics : Collections.emptyList();
            this.ast = ast;
        }

        public JsonValue toJson() {
            JsonValue root = JsonValue.ofObject();
            root.put("success", success);
            JsonValue diagArr = JsonValue.ofArray();
            for (Diagnostic d : diagnostics) {
                JsonValue dObj = JsonValue.ofObject();
                dObj.put("code", d.getCode());
                dObj.put("severity", d.getSeverity());
                dObj.put("file", d.getFile());
                dObj.put("line", d.getLine());
                dObj.put("column", d.getColumn());
                dObj.put("message", d.getMessage());
                if (d.getSuggestion() != null) {
                    dObj.put("suggestion", d.getSuggestion());
                }
                diagArr.add(dObj);
            }
            root.put("diagnostics", diagArr);
            return root;
        }
    }

    /**
     * Validates source code or an existing .zen file.
     */
    public static ValidationResult validate(String source, String filePath) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        String targetFile = filePath != null && !filePath.isEmpty() ? filePath : "schema.zen";
        Path path = Paths.get(targetFile);

        try {
            SchemaResolver resolver = new SchemaResolver();
            ZenithFile ast;

            if (source != null && !source.isEmpty()) {
                resolver.setFileOverride(path.toAbsolutePath().normalize(), source);
                ast = resolver.resolveSource(source, path);
            } else if (Files.exists(path)) {
                ast = resolver.resolve(path);
            } else {
                diagnostics.add(new Diagnostic("ZEN-404", "ERROR", targetFile, 1, 1,
                        "File not found: " + targetFile, "Provide valid source code or ensure file exists"));
                return new ValidationResult(false, diagnostics, null);
            }

            SemanticAnalyzer analyzer = new SemanticAnalyzer();
            ZenithFile analyzed = analyzer.analyze(ast);
            return new ValidationResult(true, diagnostics, analyzed);

        } catch (ParseException pe) {
            String errFile = pe.getSourceFile() != null ? pe.getSourceFile() : targetFile;
            ErrorExplainer.Explanation exp = ErrorExplainer.explain(null, pe.getRawMessage());
            diagnostics.add(new Diagnostic(exp.code, "ERROR", errFile, pe.getLine(), pe.getColumn(),
                    pe.getRawMessage(), exp.suggestion));
            return new ValidationResult(false, diagnostics, null);

        } catch (AnalysisException ae) {
            String errFile = ae.getSourceFile() != null ? ae.getSourceFile() : targetFile;
            ErrorExplainer.Explanation exp = ErrorExplainer.explain(null, ae.getRawMessage());
            diagnostics.add(new Diagnostic(exp.code, "ERROR", errFile, ae.getLine(), ae.getColumn(),
                    ae.getRawMessage(), exp.suggestion));
            return new ValidationResult(false, diagnostics, null);

        } catch (Exception e) {
            ErrorExplainer.Explanation exp = ErrorExplainer.explain(null, e.getMessage());
            diagnostics.add(new Diagnostic(exp.code, "ERROR", targetFile, 1, 1,
                    e.getMessage() != null ? e.getMessage() : e.toString(), exp.suggestion));
            return new ValidationResult(false, diagnostics, null);
        }
    }

    /**
     * Builds application from .zen file or inline source into outputDirectory.
     */
    public static JsonValue build(String source, String filePath, String outputDir) {
        JsonValue result = JsonValue.ofObject();
        ValidationResult val = validate(source, filePath);
        if (!val.success) {
            result.put("success", false);
            result.put("message", "Validation failed before compilation");
            result.put("diagnostics", val.toJson().get("diagnostics"));
            return result;
        }

        String targetDir = outputDir != null && !outputDir.trim().isEmpty() ? outputDir.trim() : "generated-app";
        Path outPath = Paths.get(targetDir);

        try {
            ProjectGenerator.writeToDirectory(val.ast, outPath);
            List<JavaSourceFile> generatedFiles = ProjectGenerator.generateAll(val.ast);

            int writtenCount = generatedFiles.size();
            JsonValue filesArr = JsonValue.ofArray();

            for (JavaSourceFile file : generatedFiles) {
                filesArr.add(file.getRelativePath().toString().replace('\\', '/'));
            }

            result.put("success", true);
            result.put("message", "Successfully compiled Zenith project into " + targetDir);
            result.put("outputDirectory", outPath.toAbsolutePath().normalize().toString());
            result.put("generatedFileCount", writtenCount);
            result.put("files", filesArr);

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "Code generation failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Returns structured metadata about the models, enums, endpoints, and config in a Zenith project.
     */
    public static JsonValue getProjectInfo(String source, String filePath) {
        ValidationResult val = validate(source, filePath);
        JsonValue info = JsonValue.ofObject();
        info.put("valid", val.success);

        if (!val.success) {
            info.put("diagnostics", val.toJson().get("diagnostics"));
            return info;
        }

        ZenithFile ast = val.ast;

        // Config info
        JsonValue cfgObj = JsonValue.ofObject();
        if (ast.hasExplicitConfig()) {
            AppConfig c = ast.getConfig();
            cfgObj.put("app", c.getApp());
            cfgObj.put("package", c.getPackage());
            cfgObj.put("port", c.getPort());
            cfgObj.put("basePath", c.getBasePath());
            cfgObj.put("database", c.getDatabase());
            cfgObj.put("auth", c.isAuthEnabled() ? "jwt" : "none");
            cfgObj.put("storage", c.getStorageProvider());
            cfgObj.put("rateLimit", c.hasRateLimit() ? c.getRawRateLimit() : "none");
            cfgObj.put("mail", c.hasMail() ? (c.getMailProvider() != null ? c.getMailProvider() : "console") : "none");
        }
        info.put("config", cfgObj);

        // Models
        JsonValue modelsArr = JsonValue.ofArray();
        for (ModelDef m : ast.getModels()) {
            JsonValue mObj = JsonValue.ofObject();
            mObj.put("name", m.getName());

            JsonValue fieldsArr = JsonValue.ofArray();
            for (FieldDef f : m.getFields()) {
                JsonValue fObj = JsonValue.ofObject();
                fObj.put("name", f.getName());
                fObj.put("type", f.getType() != null ? f.getType().getName() : "Unknown");
                fObj.put("optional", f.isOptional());
                fObj.put("isId", f.hasAnnotation("id"));
                fieldsArr.add(fObj);
            }
            mObj.put("fields", fieldsArr);

            // Endpoints
            JsonValue endObj = JsonValue.ofObject();
            endObj.put("hasCrud", m.hasCrud());
            int customCount = m.getCustomEndpoints().size();
            endObj.put("customEndpointsCount", customCount);
            mObj.put("endpoints", endObj);

            modelsArr.add(mObj);
        }
        info.put("models", modelsArr);

        // Enums
        Set<String> definedRoles = ast.getAllDefinedRoles();
        JsonValue enumsArr = JsonValue.ofArray();
        for (EnumDef e : ast.getEnums()) {
            JsonValue eObj = JsonValue.ofObject();
            eObj.put("name", e.getName());
            boolean isRole = false;
            for (String valStr : e.getValues()) {
                if (definedRoles.contains(valStr)) {
                    isRole = true;
                    break;
                }
            }
            eObj.put("isRoleEnum", isRole);
            JsonValue valsArr = JsonValue.ofArray();
            for (String v : e.getValues()) valsArr.add(v);
            eObj.put("values", valsArr);
            enumsArr.add(eObj);
        }
        info.put("enums", enumsArr);

        // Mails
        JsonValue mailsArr = JsonValue.ofArray();
        for (MailDef md : ast.getMails()) {
            mailsArr.add(md.getName());
        }
        info.put("mails", mailsArr);

        // Jobs
        JsonValue jobsArr = JsonValue.ofArray();
        for (JobDef jd : ast.getJobs()) {
            jobsArr.add(jd.getName());
        }
        info.put("jobs", jobsArr);

        return info;
    }
}
