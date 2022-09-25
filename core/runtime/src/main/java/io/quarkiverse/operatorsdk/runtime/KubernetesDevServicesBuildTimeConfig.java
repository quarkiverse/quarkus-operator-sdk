package io.quarkiverse.operatorsdk.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class KubernetesDevServicesBuildTimeConfig {

    /**
     * If kubernetes dev services should be used. (default to true)
     *
     * If this is true and kubernetes client is not configured then a kubernetes cluster
     * will be started and will be used.
     */
    @ConfigItem
    public Optional<Boolean> enabled = Optional.empty();

    /**
     * The kubernetes api server version to use. Default latest.
     */
    @ConfigItem
    public Optional<String> apiVersion;

    /**
     * The flavor to use (kind, s3 or api-only). Default to api-only
     */
    public Flavor flavor = new Flavor();

    /**
     * Indicates if the Kubernetes cluster managed by Quarkus Dev Services is shared.
     * When shared, Quarkus looks for running containers using label-based service discovery.
     * If a matching container is found, it is used, and so a second one is not started.
     * Otherwise, Dev Services for Kubernetes starts a new container.
     * <p>
     * The discovery uses the {@code quarkus-dev-service-kubernetes} label.
     * The value is configured using the {@code service-name} property.
     * <p>
     * Container sharing is only used in dev mode.
     */
    @ConfigItem(defaultValue = "true")
    public boolean shared;

    /**
     * The value of the {@code quarkus-dev-service-kubernetes} label attached to the started container.
     * This property is used when {@code shared} is set to {@code true}.
     * In this case, before starting a container, Dev Services for Kubernetes looks for a container with the
     * {@code quarkus-dev-service-kubernetes} label
     * set to the configured value. If found, it will use this container instead of starting a new one. Otherwise, it
     * starts a new container with the {@code quarkus-dev-service-kubernetes} label set to the specified value.
     * <p>
     * This property is used when you need multiple shared Kubernetes clusters.
     */
    @ConfigItem(defaultValue = "kubernetes")
    public String serviceName;

    @ConfigGroup
    public static class Flavor {
        public static enum Type {
            /**
             * kind (needs priviledge docker)
             */
            KIND("kind"),
            /**
             * k3s (needs priviledge docker)
             */
            K3S("k3s"),

            /**
             * api only
             */
            API_ONLY("api-only");

            private String flavorType;

            private Type(String flavorType) {
                this.flavorType = flavorType;
            }

            public String getFlavorType() {
                return flavorType;
            }
        }

        /**
         * Flavor which will be used to run kubernetes cluster
         */
        @ConfigItem(defaultValue = "api-only")
        public Type type = Type.API_ONLY;
    }
}
