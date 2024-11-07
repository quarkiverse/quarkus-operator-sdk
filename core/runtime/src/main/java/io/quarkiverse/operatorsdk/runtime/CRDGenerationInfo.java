package io.quarkiverse.operatorsdk.runtime;

import java.util.Map;
import java.util.Set;

import io.quarkus.runtime.annotations.IgnoreProperty;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class CRDGenerationInfo {
    private final boolean applyCRDs;
    private final boolean validateCRDs;
    private final CRDInfos crds;
    private final Set<String> generated;

    @RecordableConstructor // constructor needs to be recordable for the class to be passed around by Quarkus
    public CRDGenerationInfo(boolean applyCRDs, boolean validateCRDs, CRDInfos crds,
            Set<String> generated) {
        this.applyCRDs = applyCRDs;
        this.validateCRDs = validateCRDs;
        this.crds = crds;
        this.generated = generated;
    }

    // Needed by Quarkus: if this method isn't present, state is not properly set
    public CRDInfos getCrds() {
        return crds;
    }

    // Needed by Quarkus: if this method isn't present, state is not properly set
    public Set<String> getGenerated() {
        return generated;
    }

    // Needed by Quarkus: if this method isn't present, state is not properly set
    public boolean isApplyCRDs() {
        return applyCRDs;
    }

    public boolean shouldApplyCRD(String name) {
        return generated.contains(name);
    }

    @IgnoreProperty
    public Map<String, CRDInfo> getCRDInfosFor(String crdName) {
        return crds.getCRDInfosFor(crdName);
    }

    public boolean isValidateCRDs() {
        return validateCRDs;
    }
}
