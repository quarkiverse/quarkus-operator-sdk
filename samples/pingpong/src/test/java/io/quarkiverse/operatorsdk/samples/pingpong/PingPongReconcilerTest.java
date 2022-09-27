package io.quarkiverse.operatorsdk.samples.pingpong;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.notNullValue;

import javax.inject.Inject;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.javaoperatorsdk.operator.Operator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.KubernetesTestServer;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;

@WithKubernetesTestServer
@QuarkusTest
class PingPongReconcilerTest {

    private static final String PING_REQUEST_NAME = "myping1";
    private static final String PONG_REQUEST_NAME = PING_REQUEST_NAME + "-pong";

    @KubernetesTestServer
    KubernetesServer mockServer;

    @Inject
    Operator operator;

    @Test
    void canReconcile() {
        operator.start();

        final Ping testRequest = new Ping();
        testRequest.setMetadata(new ObjectMetaBuilder()
                .withName(PING_REQUEST_NAME)
                .withNamespace(mockServer.getClient().getNamespace())
                .build());

        // act
        mockServer.getClient().resources(Ping.class).resource(testRequest).create();

        // assert ping reconciler
        await().ignoreException(NullPointerException.class).atMost(5, MINUTES).untilAsserted(() -> {
            Ping updatedRequest = mockServer.getClient().resources(Ping.class)
                    .inNamespace(testRequest.getMetadata().getNamespace())
                    .withName(PING_REQUEST_NAME).get();
            assertThat(updatedRequest.getStatus(), is(notNullValue()));
            assertThat(updatedRequest.getStatus().getState(), is(Status.State.PROCESSED));
        });

        var createdPongs = mockServer.getClient().resources(Pong.class)
                .inNamespace(testRequest.getMetadata().getNamespace())
                .list();

        assertThat(createdPongs.getItems(), is(not(empty())));
        assertThat(createdPongs.getItems().get(0).getMetadata().getName(), is(PONG_REQUEST_NAME));

        // assert pong reconciler
        await().ignoreException(NullPointerException.class).atMost(5, MINUTES).untilAsserted(() -> {
            Pong updatedRequest = mockServer.getClient().resources(Pong.class)
                    .inNamespace(testRequest.getMetadata().getNamespace())
                    .withName(PONG_REQUEST_NAME).get();
            assertThat(updatedRequest.getStatus(), is(notNullValue()));
            assertThat(updatedRequest.getStatus().getState(), is(Status.State.PROCESSED));
        });
    }

}
