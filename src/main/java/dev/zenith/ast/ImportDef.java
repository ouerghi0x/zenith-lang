package dev.zenith.ast;

public class ImportDef {
    private final String path;
    private final int line;
    private final int column;

    public ImportDef(String path, int line, int column) {
        this.path = path;
        this.line = line;
        this.column = column;
    }

    public String getPath() {
        return path;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    @Override
    public String toString() {
        return "import \"" + path + "\"";
    }
}

