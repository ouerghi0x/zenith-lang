package dev.zenith.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A typed Spring Cloud Stream consumer declaration. */
public class StreamConsumerDef {
    private final String name;
    private String stream;
    private String group;
    private Integer concurrency;
    private Integer maxAttempts = 3;
    private Long backoffMs = 1000L;
    private Double backoffMultiplier = 2.0;
    private Long maxBackoffMs = 10000L;
    private String deadLetter;
    private final List<String> actions = new ArrayList<>();
    private String sourceFile;
    private int sourceLine;
    public StreamConsumerDef(String name) { this.name = name; }
    public String getName() { return name; }
    public String getStream() { return stream; }
    public void setStream(String value) { stream = value; }
    public String getGroup() { return group; }
    public void setGroup(String value) { group = value; }
    public Integer getConcurrency() { return concurrency; }
    public void setConcurrency(Integer value) { concurrency = value; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(Integer value) { maxAttempts = value; }
    public Long getBackoffMs() { return backoffMs; }
    public void setBackoffMs(Long value) { backoffMs = value; }
    public Double getBackoffMultiplier() { return backoffMultiplier; }
    public void setBackoffMultiplier(Double value) { backoffMultiplier = value; }
    public Long getMaxBackoffMs() { return maxBackoffMs; }
    public void setMaxBackoffMs(Long value) { maxBackoffMs = value; }
    public String getDeadLetter() { return deadLetter; }
    public void setDeadLetter(String value) { deadLetter = value; }
    public List<String> getActions() { return Collections.unmodifiableList(actions); }
    public void addAction(String value) { if (value != null && !value.isBlank()) actions.add(value); }
    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String value) { sourceFile = value; }
    public int getSourceLine() { return sourceLine; }
    public void setSourceLine(int value) { sourceLine = value; }
}
