package dev.zenith.diagnostics;

import dev.zenith.generator.trace.GenerationManifest;
import dev.zenith.generator.trace.GenerationMapping;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex streaming parser for Maven and javac output lines across Windows, Linux, and macOS.
 */
public class MavenDiagnosticParser {

    // Matches Maven/javac lines on Windows, Unix, and Maven's leading-slash Windows paths:
    // e.g. [ERROR] /C:/Users/.../OrderServiceImpl.java:[114,37] cannot find symbol
    // or   [ERROR] C:\Users\...\OrderServiceImpl.java:[114,37] cannot find symbol
    // or   [ERROR] /home/user/.../OrderServiceImpl.java:[114,37] cannot find symbol
    private static final Pattern MAVEN_JAVAC_PATTERN = Pattern.compile(
            "^\\[ERROR\\]\\s+((?:/?[A-Za-z]:[/\\\\][^:\\[\\]\\r\\n]+)|(?:/[^:\\[\\]\\r\\n]+)):?\\[(\\d+),(\\d+)\\]\\s+(.*)$"
    );

    // Fallback: [ERROR] /path/to/File.java:48: error message
    private static final Pattern SIMPLE_JAVAC_PATTERN = Pattern.compile(
            "^\\[ERROR\\]\\s+((?:/?[A-Za-z]:[/\\\\][^:\\[\\]\\r\\n]+)|(?:/[^:\\[\\]\\r\\n]+)):(\\d+):(?:(\\d+):)?\\s+(.*)$"
    );

    private final GenerationManifest manifest;

    public MavenDiagnosticParser(GenerationManifest manifest) {
        this.manifest = manifest;
    }

    public CompilerDiagnostic parseLine(String line) {
        if (line == null || !line.contains("[ERROR]")) {
            return null;
        }

        Matcher m = MAVEN_JAVAC_PATTERN.matcher(line.trim());
        String filePath = null;
        int lineNum = 1;
        int colNum = 1;
        String javacError = null;

        if (m.find()) {
            filePath = m.group(1);
            lineNum = Integer.parseInt(m.group(2));
            colNum = Integer.parseInt(m.group(3));
            javacError = m.group(4);
        } else {
            Matcher m2 = SIMPLE_JAVAC_PATTERN.matcher(line.trim());
            if (m2.find()) {
                filePath = m2.group(1);
                lineNum = Integer.parseInt(m2.group(2));
                String colStr = m2.group(3);
                colNum = colStr != null ? Integer.parseInt(colStr) : 1;
                javacError = m2.group(4);
            }
        }

        if (filePath != null) {
            GenerationMapping mapping = manifest != null ? manifest.findMapping(filePath, lineNum) : null;
            return CompilerDiagnostic.fromMapping(line, filePath, lineNum, colNum, javacError, mapping);
        }

        return null;
    }
}

