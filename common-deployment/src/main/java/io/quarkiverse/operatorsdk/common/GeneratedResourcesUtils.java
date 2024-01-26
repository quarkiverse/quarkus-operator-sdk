package io.quarkiverse.operatorsdk.common;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.quarkus.kubernetes.spi.GeneratedKubernetesResourceBuildItem;

public class GeneratedResourcesUtils {
    public static final String KUBERNETES_YAML = "kubernetes.yml";
    private static final Logger log = Logger.getLogger(GeneratedResourcesUtils.class.getName());

    public static List<HasMetadata> loadFrom(List<GeneratedKubernetesResourceBuildItem> generatedResources,
            String resourceName) {
        if (generatedResources.isEmpty()) {
            log.debugv("Couldn't load resource {0} because no resources were generated", resourceName);
            return Collections.emptyList();
        }
        var buildItem = generatedResources.stream()
                .filter(r -> resourceName.equals(r.getName()))
                .findAny();
        @SuppressWarnings("unchecked")
        List<HasMetadata> resources = (List<HasMetadata>) buildItem.map(
                bi -> FileUtils.unmarshalFrom(bi.getContent()))
                .orElseThrow(
                        () -> new IllegalArgumentException("Couldn't find resource " + resourceName +
                                " in generated resources: " + generatedResources.stream()
                                        .map(GeneratedKubernetesResourceBuildItem::getName)
                                        .collect(Collectors.toSet())));
        return resources;
    }

    public static List<HasMetadata> loadFrom(List<GeneratedKubernetesResourceBuildItem> generatedResources) {
        return loadFrom(generatedResources, KUBERNETES_YAML);
    }
}
