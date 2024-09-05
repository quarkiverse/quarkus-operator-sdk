package io.quarkiverse.operatorsdk.bundle.runtime;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface BundleConfiguration {
    String OLM_SKIP_RANGE_ANNOTATION = "olm.skipRange";
    String CONTAINER_IMAGE_ANNOTATION = "containerImage";
    String REPOSITORY_ANNOTATION = "repository";
    String CAPABILITIES_ANNOTATION = "capabilities";
    String CATEGORIES_ANNOTATION = "categories";
    String CERTIFIED_ANNOTATION = "certified";
    String ALM_EXAMPLES_ANNOTATION = "alm-examples";

    /**
     * The bundle's annotations (as found in the CSV metadata)
     */
    Map<String, String> annotations();

    default void mergeWithDefaults(BundleConfiguration defaults) {
        final var annotations = annotations();
        if (annotations != null) {
            annotations.keySet().forEach(key -> annotations.computeIfAbsent(key, k -> defaults.annotations().get(k)));
        }
    }
}
