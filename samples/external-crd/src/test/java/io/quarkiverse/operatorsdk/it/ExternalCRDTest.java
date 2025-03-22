package io.quarkiverse.operatorsdk.it;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ExternalCRDTest {
    @Inject
    KubernetesClient unused; // needed to start kube dev service

    @Inject
    Operator operator;

    @Test
    void externalCRDIsDeployed() {
        // start the operator
        operator.start();

        // external CRD should be deployed
        var externalCRD = operator.getKubernetesClient()
                .resources(CustomResourceDefinition.class)
                .withName("externals.halkyon.io")
                .get();
        Assertions.assertNotNull(externalCRD);

        operator.stop();
    }
}
