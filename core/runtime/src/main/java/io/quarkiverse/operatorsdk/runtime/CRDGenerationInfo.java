package io.quarkiverse.operatorsdk.runtime;

import java.util.*;

import io.quarkiverse.operatorsdk.common.CustomResourceInfo;
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

    // Needed by Quarkus: if this method isn't present, state is not properly set
    public Map<String, Map<String, CRDInfo>> getCrds() {
        return crds;
    }

    // Needed by Quarkus: if this method isn't present, state is not properly set
    public Set<String> getGenerated() {
        return generated;
    }

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

    public boolean isValidateCRDs() {
        return validateCRDs;
    }

    @IgnoreProperty
    public Map<String, CustomResourceInfo> getCRInfosByCRVersionFor(String crdName) {
        final var crdVersionToInfo = crds.get(crdName);
        if (crdVersionToInfo == null) {
            throw new IllegalStateException("Should have information associated with '" + crdName + "'");
        }

        Map<String, CustomResourceInfo> crVersionToCRInfo = new HashMap<>(7);
        crdVersionToInfo.forEach((crdVersion, cri) -> crVersionToCRInfo.putAll(cri.getVersions()));
        return crVersionToCRInfo;
    }
}
