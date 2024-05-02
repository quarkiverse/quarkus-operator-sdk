package io.quarkiverse.operatorsdk.runtime;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import io.quarkus.runtime.annotations.IgnoreProperty;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class CRDGenerationInfo {
    private final boolean applyCRDs;
    private final boolean validateCRDs;
    private final Map<String, Map<String, CRDInfo>> crds;
    private final Set<String> generated;

    @RecordableConstructor // constructor needs to be recordable for the class to be passed around by Quarkus
    public CRDGenerationInfo(boolean applyCRDs, boolean validateCRDs, Map<String, Map<String, CRDInfo>> crds,
            Set<String> generated) {
        this.applyCRDs = applyCRDs;
        this.validateCRDs = validateCRDs;
        this.crds = Collections.unmodifiableMap(crds);
        this.generated = generated;
    }

    // Getter required for Quarkus' RecordableConstructor, must match the associated constructor parameter name
    public Map<String, Map<String, CRDInfo>> getCrds() {
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

    public boolean shouldApplyCRD(String name) {
        return generated.contains(name);
    }

    @IgnoreProperty
    public Map<String, CRDInfo> getCRDInfosFor(String crdName) {
        return crds.get(crdName);
    }

    // Getter required for Quarkus' RecordableConstructor, must match the associated constructor parameter name
    public boolean isValidateCRDs() {
        return validateCRDs;
    }
}
