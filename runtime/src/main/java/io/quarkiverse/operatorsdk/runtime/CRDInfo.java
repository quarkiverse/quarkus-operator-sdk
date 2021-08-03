package io.quarkiverse.operatorsdk.runtime;

import java.util.Set;

import io.quarkus.runtime.annotations.RecordableConstructor;

public class CRDInfo {
    private final String crdName;
    private final String version;
    private final String filePath;
    private final Set<String> dependentClassNames;

    @RecordableConstructor // constructor needs to be recordable for the class to be passed around by Quarkus
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
}
