package io.quarkiverse.operatorsdk.deployment;

import static com.dajudge.kindcontainer.KubernetesVersionEnum.latest;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.jboss.logging.Logger;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.ContainerState;

import com.dajudge.kindcontainer.ApiServerContainer;
import com.dajudge.kindcontainer.ApiServerContainerVersion;
import com.dajudge.kindcontainer.K3sContainer;
import com.dajudge.kindcontainer.K3sContainerVersion;
import com.dajudge.kindcontainer.KindContainer;
import com.dajudge.kindcontainer.KindContainerVersion;
import com.dajudge.kindcontainer.KubernetesContainer;
import com.dajudge.kindcontainer.client.KubeConfigUtils;
import com.dajudge.kindcontainer.client.config.Cluster;
import com.dajudge.kindcontainer.client.config.ClusterSpec;
import com.dajudge.kindcontainer.client.config.Context;
import com.dajudge.kindcontainer.client.config.ContextSpec;
import com.dajudge.kindcontainer.client.config.KubeConfig;
import com.dajudge.kindcontainer.client.config.User;
import com.dajudge.kindcontainer.client.config.UserSpec;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;

import io.fabric8.kubernetes.client.Config;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.KubernetesDevServicesBuildTimeConfig;
import io.quarkiverse.operatorsdk.runtime.KubernetesDevServicesBuildTimeConfig.Flavor.Type;
import io.quarkus.deployment.Feature;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.CuratedApplicationShutdownBuildItem;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem.RunningDevService;
import io.quarkus.deployment.builditem.DevServicesSharedNetworkBuildItem;
import io.quarkus.deployment.builditem.DockerStatusBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.console.ConsoleInstalledBuildItem;
import io.quarkus.deployment.console.StartupLogCompressor;
import io.quarkus.deployment.dev.devservices.GlobalDevServicesConfig;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;
import io.quarkus.devservices.common.ConfigureUtil;
import io.quarkus.devservices.common.ContainerAddress;
import io.quarkus.devservices.common.ContainerLocator;
import io.quarkus.devservices.common.ContainerShutdownCloseable;
import io.quarkus.runtime.configuration.ConfigUtils;

@BuildSteps(onlyIfNot = IsNormal.class, onlyIf = GlobalDevServicesConfig.Enabled.class)
public class DevServicesKubernetesProcessor {
    private static final Logger log = Logger.getLogger(DevServicesKubernetesProcessor.class);
    private static final String KUBERNETES_CLIENT_MASTER_URL = "quarkus.kubernetes-client.master-url";
    private static final String DEFAULT_MASTER_URL_ENDS_WITH_SLASH = Config.DEFAULT_MASTER_URL + "/";

    static final String DEV_SERVICE_LABEL = "quarkus-dev-service-kubernetes";
    static final int KUBERNETES_PORT = 6443;
    private static final ContainerLocator KubernetesContainerLocator = new ContainerLocator(DEV_SERVICE_LABEL, KUBERNETES_PORT);

    static volatile RunningDevService devService;
    static volatile KubernetesDevServiceCfg cfg;
    static volatile boolean first = true;

    @BuildStep
    public DevServicesResultBuildItem setupKubernetesDevService(
            DockerStatusBuildItem dockerStatusBuildItem,
            LaunchModeBuildItem launchMode,
            BuildTimeOperatorConfiguration kubernetesClientBuildTimeConfig,
            List<DevServicesSharedNetworkBuildItem> devServicesSharedNetworkBuildItem,
            Optional<ConsoleInstalledBuildItem> consoleInstalledBuildItem,
            CuratedApplicationShutdownBuildItem closeBuildItem,
            LoggingSetupBuildItem loggingSetupBuildItem,
            GlobalDevServicesConfig devServicesConfig) {

        KubernetesDevServiceCfg configuration = getConfiguration(kubernetesClientBuildTimeConfig);

        if (devService != null) {
            boolean shouldShutdownTheCluster = !configuration.equals(cfg);
            if (!shouldShutdownTheCluster) {
                return devService.toBuildItem();
            }
            shutdownCluster();
            cfg = null;
        }

        StartupLogCompressor compressor = new StartupLogCompressor(
                (launchMode.isTest() ? "(test) " : "") + "Kubernetes Dev Services Starting:",
                consoleInstalledBuildItem, loggingSetupBuildItem);
        try {
            devService = startKubernetes(dockerStatusBuildItem, configuration, launchMode,
                    !devServicesSharedNetworkBuildItem.isEmpty(),
                    devServicesConfig.timeout);
            if (devService == null) {
                compressor.closeAndDumpCaptured();
            } else {
                compressor.close();
            }
        } catch (Throwable t) {
            compressor.closeAndDumpCaptured();
            throw new RuntimeException(t);
        }

        if (devService == null) {
            return null;
        }

        // Configure the watch dog
        if (first) {
            first = false;
            Runnable closeTask = () -> {
                if (devService != null) {
                    shutdownCluster();
                }
                first = true;
                devService = null;
                cfg = null;
            };
            closeBuildItem.addCloseTask(closeTask, true);
        }
        cfg = configuration;

        if (devService.isOwner()) {
            log.info(
                    "Dev Services for Kubernetes started. Other Quarkus applications in dev mode will find the "
                            + "cluster automatically.");
        }

        return devService.toBuildItem();
    }

    private void shutdownCluster() {
        if (devService != null) {
            try {
                devService.close();
            } catch (Throwable e) {
                log.error("Failed to stop the Kubernetes cluster", e);
            } finally {
                devService = null;
            }
        }
    }

    private RunningDevService startKubernetes(DockerStatusBuildItem dockerStatusBuildItem, KubernetesDevServiceCfg config,
            LaunchModeBuildItem launchMode, boolean useSharedNetwork, Optional<Duration> timeout) {
        if (!config.devServicesEnabled) {
            // explicitly disabled
            log.debug("Not starting dev services for Kubernetes, as it has been disabled in the config.");
            return null;
        }

        // Check if kubernetes-client.master-url is set
        if (ConfigUtils.isPropertyPresent(KUBERNETES_CLIENT_MASTER_URL)) {
            log.info("Not starting dev services for Kubernetes, the quarkus.kubernetes-client.master-url is configured.");
            return null;
        }

        var autoConfigMasterUrl = Config.autoConfigure(null).getMasterUrl();
        if (!DEFAULT_MASTER_URL_ENDS_WITH_SLASH.equals(autoConfigMasterUrl)) {
            log.info(
                    "Not starting dev services for Kubernetes, the kubernetes client is auto-configured. Start with '-Dkubernetes.auth.tryKubeConfig=false' or '-Dkubernetes.master=' to use dev services for Kubernetes.");
            return null;
        }

        if (!dockerStatusBuildItem.isDockerAvailable()) {
            log.warn(
                    "Docker isn't working, please configure the Kubernetes client.");
            return null;
        }

        final Optional<ContainerAddress> maybeContainerAddress = KubernetesContainerLocator.locateContainer(config.serviceName,
                config.shared,
                launchMode.getLaunchMode());

        final Supplier<RunningDevService> defaultKubernetesClusterSupplier = () -> {
            KubernetesContainer container;
            switch (config.type) {
                case API_ONLY:
                    container = new ApiServerContainer(config.apiVersion.map(ApiServerContainerVersion::valueOf)
                            .orElse(latest(ApiServerContainerVersion.class)));
                    break;
                case K3S:
                    container = new K3sContainer(config.apiVersion.map(K3sContainerVersion::valueOf)
                            .orElse(latest(K3sContainerVersion.class)));
                    break;
                case KIND:
                    container = new KindContainer(config.apiVersion.map(KindContainerVersion::valueOf)
                            .orElse(latest(KindContainerVersion.class)));
                    break;
                default:
                    throw new RuntimeException();
            }

            if (useSharedNetwork) {
                ConfigureUtil.configureSharedNetwork(container, "quarkus-operator-sdk");
            }
            if (config.serviceName != null) {
                container.withLabel(DevServicesKubernetesProcessor.DEV_SERVICE_LABEL, config.serviceName);
            }
            timeout.ifPresent(container::withStartupTimeout);

            container.start();

            KubeConfig kubeConfig = KubeConfigUtils.parseKubeConfig(container.getKubeconfig());

            return new RunningDevService(Feature.KUBERNETES_CLIENT.getName(), container.getContainerId(),
                    new ContainerShutdownCloseable(container, Feature.KUBERNETES_CLIENT.getName()),
                    getConfig(kubeConfig));
        };

        return maybeContainerAddress
                .map(containerAddress -> new RunningDevService(Feature.KUBERNETES_CLIENT.getName(),
                        containerAddress.getId(),
                        null,
                        resolveConfigurationFromRunningContainer(containerAddress)))
                .orElseGet(defaultKubernetesClusterSupplier);
    }

    private Map<String, String> getConfig(KubeConfig kubeConfig) {

        ClusterSpec cluster = kubeConfig.getClusters().get(0).getCluster();
        UserSpec user = kubeConfig.getUsers().get(0).getUser();
        return Map.of(
                "quarkus.kubernetes-client.master-url", cluster.getServer(),
                "quarkus.kubernetes-client.ca-cert-data",
                cluster.getCertificateAuthorityData(),
                "quarkus.kubernetes-client.client-cert-data",
                user.getClientCertificateData(),
                "quarkus.kubernetes-client.client-key-data", user.getClientKeyData(),
                "quarkus.kubernetes-client.client-key-algo", Config.getKeyAlgorithm(null, user.getClientKeyData()),
                "quarkus.kubernetes-client.namespace", "default");
    }

    private Map<String, String> resolveConfigurationFromRunningContainer(ContainerAddress containerAddress) {
        var dockerClient = DockerClientFactory.lazyClient();
        var container = new RunningContainer(dockerClient, containerAddress);

        return container.getKubeconfig();
    }

    private KubernetesDevServiceCfg getConfiguration(BuildTimeOperatorConfiguration cfg) {
        KubernetesDevServicesBuildTimeConfig devServicesConfig = cfg.devservices;
        return new KubernetesDevServiceCfg(devServicesConfig);
    }

    private static final class KubernetesDevServiceCfg {

        public Optional<String> apiVersion;
        public Type type;
        public boolean shared;
        public String serviceName;
        public boolean devServicesEnabled;

        public KubernetesDevServiceCfg(KubernetesDevServicesBuildTimeConfig config) {
            this.type = config.flavor.type;
            this.apiVersion = config.apiVersion;
            this.devServicesEnabled = config.enabled.orElse(true);
            this.shared = config.shared;
            this.serviceName = config.serviceName;
        }

        @Override
        public int hashCode() {
            return Objects.hash(apiVersion, devServicesEnabled, serviceName, shared, type);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!(obj instanceof KubernetesDevServiceCfg))
                return false;
            KubernetesDevServiceCfg other = (KubernetesDevServiceCfg) obj;
            return Objects.equals(apiVersion, other.apiVersion) && devServicesEnabled == other.devServicesEnabled
                    && Objects.equals(serviceName, other.serviceName) && shared == other.shared && type == other.type;
        }
    }

    private class RunningContainer implements ContainerState {
        private static final String KIND_KUBECONFIG = "/etc/kubernetes/admin.conf";
        private static final String K3S_KUBECONFIG = "/etc/rancher/k3s/k3s.yaml";
        private static final String APISERVER = "apiserver";
        private static final String PKI_BASEDIR = "/etc/kubernetes/pki";
        private static final String API_SERVER_CA = PKI_BASEDIR + "/ca.crt";
        private static final String API_SERVER_CERT = PKI_BASEDIR + "/apiserver.crt";
        private static final String API_SERVER_KEY = PKI_BASEDIR + "/apiserver.key";

        private final DockerClient dockerClient;

        private final InspectContainerResponse containerInfo;

        private final ContainerAddress containerAddress;

        public RunningContainer(DockerClient dockerClient, ContainerAddress containerAddress) {
            this.dockerClient = dockerClient;
            this.containerAddress = containerAddress;
            this.containerInfo = dockerClient.inspectContainerCmd(getContainerId()).exec();
        }

        public Map<String, String> getKubeconfig() {
            var image = getContainerInfo().getConfig().getImage();
            if (image.contains("rancher/k3s")) {
                return getConfig(
                        KubeConfigUtils.parseKubeConfig(KubeConfigUtils.replaceServerInKubeconfig(containerAddress.getUrl(),
                                getFileContentFromContainer(K3S_KUBECONFIG))));
            } else if (image.contains("kindest/node")) {
                return getConfig(
                        KubeConfigUtils.parseKubeConfig(KubeConfigUtils.replaceServerInKubeconfig(containerAddress.getUrl(),
                                getFileContentFromContainer(KIND_KUBECONFIG))));
            } else if (image.contains("k8s.gcr.io/kube-apiserver")) {
                return getConfig(getKubeconfigFromApiContainer(containerAddress.getUrl()));
            }

            throw new RuntimeException();
        }

        protected KubeConfig getKubeconfigFromApiContainer(final String url) {
            final Cluster cluster = new Cluster();
            cluster.setName(APISERVER);
            cluster.setCluster(new ClusterSpec());
            cluster.getCluster().setServer(url);
            cluster.getCluster().setCertificateAuthorityData((base64(getFileContentFromContainer(API_SERVER_CA))));
            final User user = new User();
            user.setName(APISERVER);
            user.setUser(new UserSpec());
            user.getUser().setClientKeyData(base64(getFileContentFromContainer(API_SERVER_KEY)));
            user.getUser().setClientCertificateData(base64(getFileContentFromContainer(API_SERVER_CERT)));
            final Context context = new Context();
            context.setName(APISERVER);
            context.setContext(new ContextSpec());
            context.getContext().setCluster(cluster.getName());
            context.getContext().setUser(user.getName());
            final KubeConfig config = new KubeConfig();
            config.setUsers(singletonList(user));
            config.setClusters(singletonList(cluster));
            config.setContexts(singletonList(context));
            config.setCurrentContext(context.getName());
            return config;
        }

        private String base64(final String str) {
            return Base64.getEncoder().encodeToString(str.getBytes(US_ASCII));
        }

        @Override
        public List<Integer> getExposedPorts() {
            return List.of(containerAddress.getPort());
        }

        @Override
        public DockerClient getDockerClient() {
            return this.dockerClient;
        }

        @Override
        public InspectContainerResponse getContainerInfo() {
            return containerInfo;
        }

        @Override
        public String getContainerId() {
            return this.containerAddress.getId();
        }

        public String getFileContentFromContainer(String containerPath) {
            return copyFileFromContainer(containerPath, this::readString);
        }

        String readString(final InputStream is) throws IOException {
            return new String(readBytes(is), UTF_8);
        }

        private byte[] readBytes(final InputStream is) throws IOException {
            final byte[] buffer = new byte[1024];
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            int read;
            while ((read = is.read(buffer)) > 0) {
                bos.write(buffer, 0, read);
            }
            return bos.toByteArray();
        }
    }
}
