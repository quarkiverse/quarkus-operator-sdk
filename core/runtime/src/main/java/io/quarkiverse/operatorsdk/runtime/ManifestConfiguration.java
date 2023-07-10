package io.quarkiverse.operatorsdk.runtime;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class ManifestConfiguration {

    /**
     * The namespaces that the generated manifests should watch.
     */
    @ConfigItem
    public Optional<List<String>> generateWithWatchedNamespaces;

}
