package io.quarkiverse.operatorsdk.runtime;

public class CRDInfo {
    private final String name;
    private final String path;

    public CRDInfo(String name, String path) {
        this.name = name;
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }
}
