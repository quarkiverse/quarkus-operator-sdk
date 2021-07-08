package io.quarkiverse.operatorsdk.runtime;

public class CRDInfo {
    private String name;
    private String path;

    public CRDInfo() {
    }

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

    public void setName(String name) {
        this.name = name;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
