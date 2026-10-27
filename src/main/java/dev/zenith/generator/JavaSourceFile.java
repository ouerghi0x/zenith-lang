package dev.zenith.generator;

public class JavaSourceFile {
    private final String relativePath;
    private final String content;

    public JavaSourceFile(String relativePath, String content) {
        this.relativePath = relativePath;
        this.content = content;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getContent() {
        return content;
    }
}
