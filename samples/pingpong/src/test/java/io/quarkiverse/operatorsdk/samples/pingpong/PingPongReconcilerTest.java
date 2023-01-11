package io.quarkiverse.operatorsdk.samples.pingpong;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class PingPongReconcilerTest {

    private static final String PING_REQUEST_NAME = "myping1";
    private static final String PONG_REQUEST_NAME = PING_REQUEST_NAME + "-pong";

    @Inject
    Operator operator;

    @Inject
    KubernetesClient client;

    @Test
    void canReconcile() {
        operator.start();

        final Ping testRequest = new Ping();
        testRequest.setMetadata(new ObjectMetaBuilder()
                .withName(PING_REQUEST_NAME)
                .withNamespace(client.getNamespace())
                .build());

        // act
        client.resource(testRequest).create();

        // assert ping reconciler
        await().ignoreException(NullPointerException.class).atMost(5, MINUTES).untilAsserted(() -> {
            Ping updatedRequest = client.resource(testRequest).fromServer().get();
            assertThat(updatedRequest.getStatus(), is(notNullValue()));
            assertThat(updatedRequest.getStatus().getState(), is(Status.State.PROCESSED));
        });

        var createdPongs = client.resources(Pong.class)
                .inNamespace(testRequest.getMetadata().getNamespace())
                .list();

        assertThat(createdPongs.getItems(), is(not(empty())));
        assertThat(createdPongs.getItems().get(0).getMetadata().getName(), is(PONG_REQUEST_NAME));

        // assert pong reconciler
        await().ignoreException(NullPointerException.class).atMost(5, MINUTES).untilAsserted(() -> {
            Pong updatedRequest = client.resources(Pong.class)
                    .inNamespace(testRequest.getMetadata().getNamespace())
                    .withName(PONG_REQUEST_NAME).get();
            assertThat(updatedRequest.getStatus(), is(notNullValue()));
            assertThat(updatedRequest.getStatus().getState(), is(Status.State.PROCESSED));
        });
    }

}
