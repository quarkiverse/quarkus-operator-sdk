package io.quarkiverse.operatorsdk.deployment;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.fabric8.crd.generator.CustomResourceInfo;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.Scope;
import io.quarkiverse.operatorsdk.common.ResourceInfo;

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
                info.specClassName(), info.statusClassName(), crdName, associatedControllerName);
    }

    public static ResourceInfo createFrom(Class<? extends HasMetadata> resourceClass, String resourceFullName,
            String associatedControllerName) {
        if (CustomResource.class.isAssignableFrom(resourceClass)) {
            return augment(CustomResourceInfo.fromClass((Class<? extends CustomResource>) resourceClass), resourceFullName,
                    associatedControllerName);
        } else {
            Scope scope = Namespaced.class.isAssignableFrom(resourceClass) ? Scope.NAMESPACED : Scope.CLUSTER;

            return new ResourceInfo(HasMetadata.getGroup(resourceClass), HasMetadata.getVersion(resourceClass),
                    HasMetadata.getKind(resourceClass), HasMetadata.getSingular(resourceClass),
                    HasMetadata.getPlural(resourceClass), new String[0],
                    false, false, scope, resourceClass.getCanonicalName(),
                    Optional.empty(), Optional.empty(),
                    resourceFullName, associatedControllerName);
        }
    }
}
