package io.quarkiverse.operatorsdk.deployment;

import java.util.HashMap;
import java.util.Map;

import io.quarkiverse.operatorsdk.common.ClassUtils;
import io.quarkiverse.operatorsdk.runtime.ResourceInfo;

public class ResourceControllerMapping {
    private final Map<String, Map<String, ResourceInfo>> resourceFullNameToVersionToInfos = new HashMap<>(7);

    public Map<String, ResourceInfo> getResourceInfos(String resourceFullName) {
        final var infos = resourceFullNameToVersionToInfos.get(resourceFullName);
        if (infos == null) {
            throw new IllegalStateException("Should have information associated with '" + resourceFullName + "'");
        }
        return infos;
    }

    public void add(io.fabric8.crd.generator.CustomResourceInfo info, String crdName, String associatedControllerName) {
        final var version = info.version();
        final var versionsForCR = resourceFullNameToVersionToInfos.computeIfAbsent(crdName, s -> new HashMap<>());
        final var cri = versionsForCR.get(version);
        if (cri != null) {
            throw new IllegalStateException("Cannot process controller '" + associatedControllerName +
                    "' because a controller (" + cri.getControllerName() + ") is already associated with CRD "
                    + crdName + " with version " + version);
        }

        final var converted = augment(info, crdName, associatedControllerName);
        versionsForCR.put(version, converted);
    }

    private static ResourceInfo augment(io.fabric8.crd.generator.CustomResourceInfo info,
            String crdName, String associatedControllerName) {
        return new ResourceInfo(
                info.group(), info.version(), info.kind(), info.singular(), info.plural(), info.shortNames(),
                info.storage(),
                info.served(), info.scope(), info.crClassName(),
                info.statusClassName().map(ClassUtils::isStatusNotVoid).orElse(false), crdName,
                associatedControllerName);
    }
}
