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

    public void add(io.fabric8.crd.generator.CustomResourceInfo info, String crdName) {
        final var converted = new CustomResourceInfo(
                info.group(), info.version(), info.kind(), info.singular(), info.plural(), info.shortNames(), info.storage(),
                info.served(), info.scope(), info.crClassName(),
                info.specClassName(), info.statusClassName());
        crdNameToCRVersionToCRInfos.computeIfAbsent(crdName, s -> new HashMap<>()).put(info.version(), converted);
    }
}
