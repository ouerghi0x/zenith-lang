package dev.zenith.analyzer;

public class AnalysisException extends RuntimeException {
    public AnalysisException(String message) {
        super("Semantic analysis error: " + message);
    }
}
