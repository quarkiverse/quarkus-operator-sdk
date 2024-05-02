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

    // Getter required for Quarkus' RecordableConstructor, must match the associated constructor parameter name
    public CRDInfos getCrds() {
        return crds;
    }

    // Getter required for Quarkus' RecordableConstructor, must match the associated constructor parameter name
    public Set<String> getGenerated() {
        return generated;
    }

    // Getter required for Quarkus' RecordableConstructor, must match the associated constructor parameter name
    public boolean isApplyCRDs() {
        return applyCRDs;
    }

    @IgnoreProperty
    public Map<String, CRDInfo> getCRDInfosFor(String crdName) {
        return crds.getOrCreateCRDSpecVersionToInfoMapping(crdName);
    }

    // Getter required for Quarkus' RecordableConstructor, must match the associated constructor parameter name
    public boolean isValidateCRDs() {
        return validateCRDs;
    }
}
