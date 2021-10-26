package io.quarkiverse.operatorsdk.runtime;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.quarkiverse.operatorsdk.common.CustomResourceInfo;
import io.quarkus.runtime.annotations.IgnoreProperty;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class CRDGenerationInfo {
    private final boolean applyCRDs;
    private final boolean validateCRDs;
    private final Map<String, Map<String, CRDInfo>> crds;
    private final Set<String> generated;
    private final Map<String, CustomResourceInfo> controllerToCRI;

    @RecordableConstructor // constructor needs to be recordable for the class to be passed around by Quarkus
    public CRDGenerationInfo(boolean applyCRDs, boolean validateCRDs, Map<String, Map<String, CRDInfo>> crds,
            Set<String> generated) {
        this.applyCRDs = applyCRDs;
        this.validateCRDs = validateCRDs;
        this.crds = Collections.unmodifiableMap(crds);
        this.generated = generated;
        final var mappings = new HashMap<String, CustomResourceInfo>(crds.size());
        crds.values().forEach(crdInfos -> crdInfos.values().forEach(info -> info.getVersions().values().forEach(cri -> {
            final var controllerName = cri.getControllerName();
            if (!mappings.containsKey(controllerName)) {
                mappings.put(controllerName, cri);
            }
        })));
        this.controllerToCRI = Collections.unmodifiableMap(mappings);
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
    public Map<String, CustomResourceInfo> getControllerToCustomResourceMappings() {
        return controllerToCRI;
    }
}
