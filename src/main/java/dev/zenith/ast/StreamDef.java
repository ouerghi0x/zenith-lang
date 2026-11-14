package dev.zenith.ast;

public class StreamDef {
    private final String name;
    private final String topic;
    private final String payload;
    private final String broker;
    private final Integer partitions;
    private String sourceFile;
    private int sourceLine;

    public StreamDef(String name, String topic, String payload, String broker, Integer partitions) {
        this.name = name; this.topic = topic; this.payload = payload; this.broker = broker;
        this.partitions = partitions;
    }
    public String getName() { return name; }
    public String getTopic() { return topic; }
    public String getPayload() { return payload; }
    public String getBroker() { return broker; }
    public Integer getPartitions() { return partitions; }
    public String getSourceFile() { return sourceFile; }
    public int getSourceLine() { return sourceLine; }
    public void setSourceFile(String value) { sourceFile = value; }
    public void setSourceLine(int value) { sourceLine = value; }
}
