package io.quarkiverse.operatorsdk.deployment;

import java.util.HashMap;
import java.util.Map;

import io.quarkiverse.operatorsdk.common.CustomResourceInfo;

public class CustomResourceControllerMapping {
    private final Map<String, Map<String, CustomResourceInfo>> crdNameToCRVersionToCRInfos = new HashMap<>(7);

    public Map<String, CustomResourceInfo> getCustomResourceInfos(String crdName) {
        final var customResourceInfos = crdNameToCRVersionToCRInfos.get(crdName);
        if (customResourceInfos == null) {
            throw new IllegalStateException("Should have information associated with '" + crdName + "'");
        }
        return customResourceInfos;
    }

    public void add(io.fabric8.crd.generator.CustomResourceInfo info, String crdName, String associatedControllerName) {
        final var version = info.version();
        final var versionsForCR = crdNameToCRVersionToCRInfos.computeIfAbsent(crdName, s -> new HashMap<>());
        final var cri = versionsForCR.get(version);
        if (cri != null) {
            throw new IllegalStateException("Cannot process controller '" + associatedControllerName +
                    "' because a controller (" + cri.getControllerName() + ") is already associated with CRD "
                    + crdName + " with version " + version);
        }

        final var converted = new CustomResourceInfo(
                info.group(), version, info.kind(), info.singular(), info.plural(), info.shortNames(), info.storage(),
                info.served(), info.scope(), info.crClassName(),
                info.specClassName(), info.statusClassName(), crdName, associatedControllerName);
        versionsForCR.put(version, converted);
    }
}
