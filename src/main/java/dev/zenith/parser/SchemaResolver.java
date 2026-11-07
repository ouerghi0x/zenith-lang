package dev.zenith.parser;

import dev.zenith.analyzer.AnalysisException;
import dev.zenith.ast.*;
import dev.zenith.lexer.Lexer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class SchemaResolver {

    private final Set<Path> visitedFiles = new LinkedHashSet<>();
    private final Deque<Path> importStack = new ArrayDeque<>();
    private final Map<Path, String> fileOverrides = new HashMap<>();

    public void setFileOverride(Path path, String content) {
        if (path != null && content != null) {
            fileOverrides.put(path.toAbsolutePath().normalize(), content);
        }
    }

    public ZenithFile resolve(Path rootFile) throws IOException {
        visitedFiles.clear();
        importStack.clear();
        Path canonicalRoot = rootFile.toAbsolutePath().normalize();
        return resolveFile(canonicalRoot);
    }

    public ZenithFile resolveSource(String rootSource, Path basePath) throws IOException {
        visitedFiles.clear();
        importStack.clear();

        String rootFileName = basePath != null ? basePath.getFileName().toString() : "main.zen";
        Lexer lexer = new Lexer(rootSource);
        Parser parser = new Parser(lexer.tokenize(), rootFileName);
        ZenithFile rootFile = parser.parse();

        Path currentDir = basePath != null ? basePath.toAbsolutePath().normalize() : Paths.get(".").toAbsolutePath().normalize();
        if (Files.isRegularFile(currentDir)) {
            currentDir = currentDir.getParent();
        }

        AppConfig finalConfig = rootFile.hasExplicitConfig() ? rootFile.getConfig() : null;
        String configSource = rootFile.hasExplicitConfig() ? "root schema" : null;

        List<EnumDef> allEnums = new ArrayList<>(rootFile.getEnums());
        List<ModelDef> allModels = new ArrayList<>(rootFile.getModels());
        List<DtoDef> allDtos = new ArrayList<>(rootFile.getDtos());
        List<SeedDef> allSeeds = new ArrayList<>(rootFile.getSeeds());
        List<JobDef> allJobs = new ArrayList<>(rootFile.getJobs());
        List<ImportDef> allImports = new ArrayList<>(rootFile.getImports());

        for (ImportDef imp : rootFile.getImports()) {
            Path targetPath = currentDir.resolve(imp.getPath()).normalize();
            if (!fileOverrides.containsKey(targetPath) && !Files.exists(targetPath)) {
                throw new ParseException("Imported file not found: '" + imp.getPath() + "'", imp.getLine(), imp.getColumn(), rootFileName);
            }

            ZenithFile importedFile = resolveFile(targetPath);
            if (importedFile != null) {
                if (importedFile.hasExplicitConfig()) {
                    if (finalConfig != null) {
                        throw new AnalysisException("Duplicate 'config' block found in '" + targetPath.getFileName()
                                + "'. Configuration was already defined in " + configSource + ".");
                    }
                    finalConfig = importedFile.getConfig();
                    configSource = "'" + targetPath.getFileName() + "'";
                }

                allEnums.addAll(importedFile.getEnums());
                allModels.addAll(importedFile.getModels());
                allDtos.addAll(importedFile.getDtos());
                allSeeds.addAll(importedFile.getSeeds());
                allJobs.addAll(importedFile.getJobs());
            }
        }

        boolean hasConfig = (configSource != null);
        if (finalConfig == null) {
            finalConfig = AppConfig.defaults();
        }

        return new ZenithFile(finalConfig, allEnums, allModels, allDtos, allSeeds, allJobs, allImports, hasConfig);
    }

    public Set<Path> getResolvedFiles() {
        return Collections.unmodifiableSet(visitedFiles);
    }

    private ZenithFile resolveFile(Path filePath) throws IOException {
        Path normalizedPath = filePath.toAbsolutePath().normalize();

        if (importStack.contains(normalizedPath)) {
            StringBuilder cycle = new StringBuilder();
            List<Path> list = new ArrayList<>(importStack);
            Collections.reverse(list);
            for (Path p : list) {
                cycle.append(p.getFileName()).append(" -> ");
            }
            cycle.append(normalizedPath.getFileName());
            throw new AnalysisException("Circular import detected: " + cycle);
        }

        if (visitedFiles.contains(normalizedPath)) {
            return null; // Diamond import already loaded
        }

        if (!fileOverrides.containsKey(normalizedPath) && !Files.exists(normalizedPath)) {
            throw new AnalysisException("Schema file not found: " + normalizedPath);
        }

        importStack.push(normalizedPath);

        String source = fileOverrides.containsKey(normalizedPath)
                ? fileOverrides.get(normalizedPath)
                : Files.readString(normalizedPath);
        String currentFileName = normalizedPath.getFileName().toString();
        Lexer lexer = new Lexer(source);
        Parser parser = new Parser(lexer.tokenize(), currentFileName);
        ZenithFile currentFile = parser.parse();

        AppConfig finalConfig = currentFile.hasExplicitConfig() ? currentFile.getConfig() : null;
        Path configSourceFile = currentFile.hasExplicitConfig() ? normalizedPath : null;

        List<EnumDef> allEnums = new ArrayList<>(currentFile.getEnums());
        List<ModelDef> allModels = new ArrayList<>(currentFile.getModels());
        List<DtoDef> allDtos = new ArrayList<>(currentFile.getDtos());
        List<SeedDef> allSeeds = new ArrayList<>(currentFile.getSeeds());
        List<JobDef> allJobs = new ArrayList<>(currentFile.getJobs());
        List<ImportDef> allImports = new ArrayList<>(currentFile.getImports());

        Path currentDir = normalizedPath.getParent();
        if (currentDir == null) {
            currentDir = Paths.get(".");
        }

        for (ImportDef imp : currentFile.getImports()) {
            Path targetPath = currentDir.resolve(imp.getPath()).normalize();
            if (!fileOverrides.containsKey(targetPath) && !Files.exists(targetPath)) {
                throw new ParseException("Imported file not found: '" + imp.getPath() + "'",
                        imp.getLine(), imp.getColumn(), currentFileName);
            }

            ZenithFile importedFile = resolveFile(targetPath);
            if (importedFile != null) {
                if (importedFile.hasExplicitConfig()) {
                    if (finalConfig != null) {
                        throw new AnalysisException("Duplicate 'config' block found in '" + targetPath.getFileName()
                                + "'. Configuration was already defined in '" + (configSourceFile != null ? configSourceFile.getFileName() : "root") + "'.");
                    }
                    finalConfig = importedFile.getConfig();
                    configSourceFile = targetPath;
                }

                allEnums.addAll(importedFile.getEnums());
                allModels.addAll(importedFile.getModels());
                allDtos.addAll(importedFile.getDtos());
                allSeeds.addAll(importedFile.getSeeds());
                allJobs.addAll(importedFile.getJobs());
            }
        }

        importStack.pop();
        visitedFiles.add(normalizedPath);

        boolean hasConfig = (configSourceFile != null);
        if (finalConfig == null) {
            finalConfig = AppConfig.defaults();
        }

        return new ZenithFile(finalConfig, allEnums, allModels, allDtos, allSeeds, allJobs, allImports, hasConfig);
    }
}
