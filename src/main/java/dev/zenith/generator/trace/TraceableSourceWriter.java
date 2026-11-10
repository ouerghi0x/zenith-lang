package dev.zenith.generator.trace;

import dev.zenith.ast.SourceLocation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Encapsulates emitted code generation tracking, counting lines across
 * Unix (\n) and Windows (\r\n) newlines, and capturing generator call frames.
 */
public class TraceableSourceWriter {
    private final StringBuilder buffer = new StringBuilder();
    private final String targetRelativePath;
    private final List<GenerationMapping> mappings = new ArrayList<>();
    private int currentLine = 1;

    public TraceableSourceWriter(String targetRelativePath) {
        this.targetRelativePath = targetRelativePath != null ? targetRelativePath.replace('\\', '/') : "";
    }

    public TraceableSourceWriter append(String str) {
        if (str == null) return this;
        buffer.append(str);
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == '\n') {
                currentLine++;
            }
        }
        return this;
    }

    public TraceableSourceWriter append(Object obj) {
        return append(String.valueOf(obj));
    }

    public int getCurrentLine() {
        return currentLine;
    }

    public static int countLines(String text) {
        if (text == null || text.isEmpty()) return 1;
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        return count;
    }

    public String getTargetRelativePath() {
        return targetRelativePath;
    }

    public TraceScope track(String elementName, SourceLocation zenOrigin) {
        return track(elementName, zenOrigin, null);
    }

    public TraceScope track(String elementName, SourceLocation zenOrigin, String snippet) {
        int startLine = currentLine;
        StackWalker.StackFrame caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames.filter(f -> !f.getClassName().equals(TraceableSourceWriter.class.getName())
                                                && !f.getClassName().equals(TraceScope.class.getName()))
                        .findFirst()
                        .orElse(null));
        return new TraceScope(elementName, zenOrigin, snippet, startLine, caller);
    }

    public void addMapping(String elementName, int startLine, int endLine,
                           String generatorClass, String generatorMethod, int generatorLine,
                           SourceLocation zenOrigin, String snippet) {
        mappings.add(new GenerationMapping(
                targetRelativePath, elementName, startLine, endLine,
                generatorClass, generatorMethod, generatorLine, zenOrigin, snippet
        ));
    }

    public class TraceScope implements AutoCloseable {
        private final String elementName;
        private final SourceLocation origin;
        private final String snippet;
        private final int startLine;
        private final StackWalker.StackFrame caller;

        public TraceScope(String elementName, SourceLocation origin, String snippet,
                          int startLine, StackWalker.StackFrame caller) {
            this.elementName = elementName;
            this.origin = origin;
            this.snippet = snippet;
            this.startLine = startLine;
            this.caller = caller;
        }

        @Override
        public void close() {
            int endLine = Math.max(startLine, currentLine);
            mappings.add(new GenerationMapping(
                    targetRelativePath, elementName, startLine, endLine,
                    caller != null ? caller.getClassName() : "dev.zenith.generator.UnknownGenerator",
                    caller != null ? caller.getMethodName() : "unknownMethod",
                    caller != null ? caller.getLineNumber() : -1,
                    origin,
                    snippet
            ));
        }
    }

    public String getContent() {
        return buffer.toString();
    }

    public List<GenerationMapping> getMappings() {
        return Collections.unmodifiableList(mappings);
    }

    @Override
    public String toString() {
        return getContent();
    }
}

