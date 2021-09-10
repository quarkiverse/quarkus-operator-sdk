package io.quarkiverse.operatorsdk.runtime;

import java.util.Map;
import java.util.Set;

import io.quarkus.runtime.annotations.RecordableConstructor;

public class CRDInfo {
    private final Map<String, CustomResourceInfo> versions;
    private final String crdName;
    private final String crdSpecVersion;
    private final String filePath;
    private final Set<String> dependentClassNames;

    @RecordableConstructor // constructor needs to be recordable for the class to be passed around by Quarkus
    public CRDInfo(String crdName, String crdSpecVersion, String filePath, Set<String> dependentClassNames,
            Map<String, CustomResourceInfo> versions) {
        this.crdName = crdName;
        this.crdSpecVersion = crdSpecVersion;
        this.filePath = filePath;
        this.dependentClassNames = dependentClassNames;
        this.versions = versions;
    }

    public String getCrdName() {
        return this.crdName;
    }

    public String getCrdSpecVersion() {
        return this.crdSpecVersion;
    }

    public String getFilePath() {
        return this.filePath;
    }

    public Set<String> getDependentClassNames() {
        return this.dependentClassNames;
    }

    public Map<String, CustomResourceInfo> getVersions() {
        return versions;
    }

}
