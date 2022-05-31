package io.quarkiverse.operatorsdk.it;

import io.quarkus.test.kubernetes.client.KubernetesServerTestResource;

public class CustomKubernetesServerTestResource extends KubernetesServerTestResource {

    @Override
    protected void configureServer() {
        super.configureServer();
        server.expect().get().withPath("/version")
                .andReturn(200, "{\"major\": \"13\", \"minor\": \"37\"}").always();
    }
}
