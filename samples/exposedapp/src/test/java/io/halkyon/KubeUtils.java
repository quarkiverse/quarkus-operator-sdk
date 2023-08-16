package io.halkyon;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.testcontainers.shaded.com.google.common.io.Files;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;

public class KubeUtils {

    public static final String HELM_TEST = "helmtest";

    public static File generateConfigFromClient(KubernetesClient client) {
        try {
            var actualConfig = client.getConfiguration();
            Config res = new Config();
            res.setApiVersion("v1");
            res.setKind("Config");
            res.setClusters(createCluster(actualConfig));
            res.setContexts(createContext(actualConfig));
            res.setUsers(createUser(actualConfig));
            res.setCurrentContext(HELM_TEST);

            File targetFile = new File("target", "devservice-kubeconfig.yaml");
            String yaml = client.getKubernetesSerialization().asYaml(res);

            Files.write(yaml, targetFile, StandardCharsets.UTF_8);
            return targetFile;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<NamedAuthInfo> createUser(io.fabric8.kubernetes.client.Config actualConfig) {
        var user = new NamedAuthInfo();
        user.setName(HELM_TEST);
        user.setUser(new AuthInfo());
        user.getUser().setClientCertificateData(actualConfig.getClientCertData());
        user.getUser().setClientKeyData(actualConfig.getClientKeyData());
        user.getUser().setPassword(actualConfig.getPassword());
        return List.of(user);
    }

    private static List<NamedContext> createContext(io.fabric8.kubernetes.client.Config actualConfig) {
        var context = new NamedContext();
        context.setName(HELM_TEST);
        context.setContext(new Context());
        context.getContext().setCluster(HELM_TEST);
        context.getContext().setUser(HELM_TEST);
        context.getContext().setNamespace(actualConfig.getNamespace());

        return List.of(context);
    }

    private static List<NamedCluster> createCluster(io.fabric8.kubernetes.client.Config actualConfig) {
        var cluster = new NamedCluster();
        cluster.setName(HELM_TEST);
        cluster.setCluster(new Cluster());
        cluster.getCluster().setServer(actualConfig.getMasterUrl());
        cluster.getCluster().setCertificateAuthorityData(actualConfig.getCaCertData());
        return List.of(cluster);
    }

}
