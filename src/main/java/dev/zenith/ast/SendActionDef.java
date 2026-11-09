package dev.zenith.ast;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an email dispatch action statement within an endpoint or job:
 * send OrderConfirmation(order = entity, customer = entity.customer)
 */
public class SendActionDef {
    private final String mailName;
    private final Map<String, String> arguments;
    private String sourceFile;
    private int sourceLine;

    public SendActionDef(String mailName, Map<String, String> arguments) {
        this.mailName = mailName;
        this.arguments = arguments != null ? arguments : new LinkedHashMap<>();
    }

    public String getMailName() {
        return mailName;
    }

    public Map<String, String> getArguments() {
        return Collections.unmodifiableMap(arguments);
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public int getSourceLine() {
        return sourceLine;
    }

    public void setSourceLine(int sourceLine) {
        this.sourceLine = sourceLine;
    }
}

