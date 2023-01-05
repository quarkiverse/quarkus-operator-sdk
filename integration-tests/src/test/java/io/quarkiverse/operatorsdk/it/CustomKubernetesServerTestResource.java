package io.quarkiverse.operatorsdk.it;

import static io.quarkiverse.operatorsdk.it.EmptyReconciler.FROM_ENV_NS1;
import static io.quarkiverse.operatorsdk.it.EmptyReconciler.FROM_ENV_NS2;
import static io.quarkiverse.operatorsdk.it.NamespaceFromEnvReconciler.FROM_ENV_VAR_NS;

import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.quarkus.test.kubernetes.client.KubernetesServerTestResource;

public class CustomKubernetesServerTestResource extends KubernetesServerTestResource {

    @Override
    protected void configureServer() {
        super.configureServer();

        // hack: set system properties here so that they are set before the app is started
        System.setProperty(NamespaceFromEnvReconciler.ENV_VAR_NAME, FROM_ENV_VAR_NS);
        System.setProperty("QUARKUS_OPERATOR_SDK_CONTROLLERS_" + EmptyReconciler.NAME.toUpperCase()
                + "_NAMESPACES", FROM_ENV_NS1 + ",   " + FROM_ENV_NS2);
        System.setProperty(VariableNSReconciler.ENV_VAR_NAME, VariableNSReconciler.EXPECTED_NS_VALUE);
        System.setProperty("QUARKUS_OPERATOR_SDK_CONTROLLERS_" + ReconcilerUtils.getDefaultNameFor(
                KeycloakController.class).toUpperCase() + "_NAMESPACES", KeycloakController.FROM_ENV);

        server.expect().get().withPath("/version")
                .andReturn(200, "{\"major\": \"13\", \"minor\": \"37\"}").always();
    }
}
