package dev.zenith.ast;

public class SeedRef {
    private final String modelName;
    private final int index;

    public SeedRef(String modelName, int index) {
        this.modelName = modelName;
        this.index = index;
    }

    public String getModelName() {
        return modelName;
    }

    public int getIndex() {
        return index;
    }

    @Override
    public String toString() {
        return modelName + "[" + index + "]";
    }
}

