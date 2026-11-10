package dev.zenith.generator.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manifest containing the full bidirectional source mapping between
 * generated project files, compiler generator methods, and .zen source locations.
 */
public class GenerationManifest {
    private final String zenithVersion;
    private final String generatedAt;
    private final String rootSchema;
    private final List<GenerationMapping> mappings;

    public GenerationManifest(String rootSchema) {
        this("1.0.0", java.time.Instant.now().toString(), rootSchema, new ArrayList<>());
    }

    public GenerationManifest(String zenithVersion, String generatedAt, String rootSchema, List<GenerationMapping> mappings) {
        this.zenithVersion = zenithVersion != null ? zenithVersion : "1.0.0";
        this.generatedAt = generatedAt != null ? generatedAt : java.time.Instant.now().toString();
        this.rootSchema = rootSchema != null ? rootSchema.replace('\\', '/') : "schema.zen";
        this.mappings = mappings != null ? new ArrayList<>(mappings) : new ArrayList<>();
    }

    public void addMapping(GenerationMapping mapping) {
        if (mapping != null) {
            mappings.add(mapping);
        }
    }

    public void addAllMappings(List<GenerationMapping> mappingsToAdd) {
        if (mappingsToAdd != null) {
            mappings.addAll(mappingsToAdd);
        }
    }

    public String getZenithVersion() {
        return zenithVersion;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public String getRootSchema() {
        return rootSchema;
    }

    public List<GenerationMapping> getMappings() {
        return Collections.unmodifiableList(mappings);
    }

    /**
     * Finds the closest mapping matching the generated target file and line number.
     */
    public GenerationMapping findMapping(String generatedFilePath, int lineNumber) {
        if (generatedFilePath == null) return null;
        String normalizedPath = generatedFilePath.replace('\\', '/');

        // Extract filename from normalizedPath (e.g. OrderServiceImpl.java)
        String fileName = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);

        GenerationMapping bestCovering = null;
        int bestCoveringRange = Integer.MAX_VALUE;
        GenerationMapping fallbackFileMatch = null;

        for (GenerationMapping m : mappings) {
            String mapFile = m.getGeneratedFile();
            boolean pathMatches = normalizedPath.endsWith(mapFile) || mapFile.endsWith(normalizedPath)
                    || mapFile.endsWith(fileName);
            if (pathMatches) {
                if (m.coversLine(lineNumber)) {
                    int range = m.getEndLine() - m.getStartLine();
                    if (range < bestCoveringRange) {
                        bestCoveringRange = range;
                        bestCovering = m;
                    }
                }
                if (fallbackFileMatch == null) {
                    fallbackFileMatch = m;
                }
            }
        }
        return bestCovering != null ? bestCovering : fallbackFileMatch;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"zenithVersion\": \"").append(escape(zenithVersion)).append("\",\n");
        sb.append("  \"generatedAt\": \"").append(escape(generatedAt)).append("\",\n");
        sb.append("  \"rootSchema\": \"").append(escape(rootSchema)).append("\",\n");
        sb.append("  \"mappings\": [\n");
        for (int i = 0; i < mappings.size(); i++) {
            sb.append(mappings.get(i).toJson());
            if (i < mappings.size() - 1) {
                sb.append(",\n");
            } else {
                sb.append("\n");
            }
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}

