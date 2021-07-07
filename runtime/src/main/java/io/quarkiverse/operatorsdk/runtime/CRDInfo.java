package io.quarkiverse.operatorsdk.runtime;

import java.util.Set;

public class CRDInfo {
    private String crdName;
    private String version;
    private String filePath;
    private Set<String> dependentClassNames;

    // Needed by Quarkus: class needs to be serializable
    public CRDInfo() {
    }

    public CRDInfo(String crdName, String version, String filePath, Set<String> dependentClassNames) {
        this.crdName = crdName;
        this.version = version;
        this.filePath = filePath;
        this.dependentClassNames = dependentClassNames;
    }

    public String getCrdName() {
        return this.crdName;
    }

    public String getVersion() {
        return this.version;
    }

    public String getFilePath() {
        return this.filePath;
    }

    public Set<String> getDependentClassNames() {
        return this.dependentClassNames;
    }

    // Needed by Quarkus: class needs to be serializable
    public void setCrdName(String crdName) {
        this.crdName = crdName;
    }

    // Needed by Quarkus: class needs to be serializable
    public void setVersion(String version) {
        this.version = version;
    }

    // Needed by Quarkus: class needs to be serializable
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    // Needed by Quarkus: class needs to be serializable
    public void setDependentClassNames(Set<String> dependentClassNames) {
        this.dependentClassNames = dependentClassNames;
    }
}