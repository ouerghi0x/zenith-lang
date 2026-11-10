package dev.zenith.generator;

import dev.zenith.generator.trace.GenerationMapping;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JavaSourceFile {
    private final String relativePath;
    private final String content;
    private final List<GenerationMapping> mappings;

    public JavaSourceFile(String relativePath, String content) {
        this(relativePath, content, Collections.emptyList());
    }

    public JavaSourceFile(String relativePath, String content, List<GenerationMapping> mappings) {
        this.relativePath = relativePath != null ? relativePath.replace('\\', '/') : "";
        this.content = content != null ? content : "";
        this.mappings = mappings != null ? new ArrayList<>(mappings) : Collections.emptyList();
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getContent() {
        return content;
    }

    public List<GenerationMapping> getMappings() {
        return Collections.unmodifiableList(mappings);
    }
}
