package io.halkyon;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ExposedAppReconcilerTest {

    public static final String TEST_APP = "test-app";

    @Inject
    protected KubernetesClient client;

    @Test
    void reconcileShouldWork() {
        final var app = new ExposedApp();
        final var metadata = new ObjectMetaBuilder()
                .withName(TEST_APP)
                .withNamespace(client.getNamespace())
                .build();
        app.setMetadata(metadata);
        app.getSpec().setImageRef("group/imageName:tag");

        client.resource(app).create();

        await().ignoreException(NullPointerException.class).atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // check that we create the deployment
            final var deployment = client.apps().deployments()
                    .inNamespace(metadata.getNamespace())
                    .withName(metadata.getName()).get();
            final var maybeFirstContainer = deployment.getSpec().getTemplate().getSpec().getContainers()
                    .stream()
                    .findFirst();
            assertThat(maybeFirstContainer.isPresent(), is(true));
            final var firstContainer = maybeFirstContainer.get();
            assertThat(firstContainer.getImage(), is(app.getSpec().getImageRef()));

            // check that the service is created
            final var service = client.services()
                    .inNamespace(metadata.getNamespace())
                    .withName(metadata.getName()).get();
            final var port = service.getSpec().getPorts().get(0).getPort();
            assertThat(port, is(8080));

            // check that the ingress is created
            final var ingress = client.network().v1().ingresses()
                    .inNamespace(metadata.getNamespace()).withName(metadata.getName()).get();
            // not using nginx controller on k3s
            /*
             * final var annotations = ingress.getMetadata().getAnnotations();
             * assertThat(annotations.size(), is(2));
             * assertThat(annotations.get("kubernetes.io/ingress.class"), is("nginx"));
             */
            final var rules = ingress.getSpec().getRules();
            assertThat(rules.size(), is(1));
            final var paths = rules.get(0).getHttp().getPaths();
            assertThat(paths.size(), is(1));
            final var path = paths.get(0);
            assertThat(path.getPath(), is("/"));
            assertThat(path.getPathType(), is("Prefix"));
            final var serviceBackend = path.getBackend().getService();
            assertThat(serviceBackend.getName(), is(service.getMetadata().getName()));
            assertThat(serviceBackend.getPort().getNumber(), is(port));
        });

        client.resource(app).delete();

        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(client.apps().deployments()
                    .inNamespace(metadata.getNamespace())
                    .withName(metadata.getName()).get(), nullValue());
            assertThat(client.services()
                    .inNamespace(metadata.getNamespace())
                    .withName(metadata.getName()).get(), nullValue());
            assertThat(client.network().v1().ingresses()
                    .inNamespace(metadata.getNamespace())
                    .withName(metadata.getName()).get(), nullValue());
        });
    }
}
