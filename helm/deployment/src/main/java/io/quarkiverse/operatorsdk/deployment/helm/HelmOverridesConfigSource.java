package io.quarkiverse.operatorsdk.deployment.helm;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_ALL_NAMESPACES;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigSource;

public class HelmOverridesConfigSource implements ConfigSource {
    private static final Map<String, String> configuration = new HashMap<>();

    static {
        configuration.put("quarkus.helm.app-version", "${quarkus.application.version");
        configuration.put("quarkus.helm.values.watchNamespaces.paths", "watchNamespaces");
        configuration.put("quarkus.helm.values.watchNamespaces.description",
                "namespaces to be watched, either a list of comma-separated namespace names,"
                        + " `JOSDK_ALL_NAMESPACES` to watch all namespaces, `JOSDK_WATCH_CURRENT` to watch only the namespace in which the operator is deployed.");
        configuration.put("quarkus.helm.values.watchNamespaces.value", WATCH_ALL_NAMESPACES);
    }

    @Override
    public Set<String> getPropertyNames() {
        return configuration.keySet();
    }

    @Override
    public String getValue(String propertyName) {
        return configuration.getOrDefault(propertyName, null);
    }

    @Override
    public String getName() {
        return HelmOverridesConfigSource.class.getSimpleName();
    }
}
