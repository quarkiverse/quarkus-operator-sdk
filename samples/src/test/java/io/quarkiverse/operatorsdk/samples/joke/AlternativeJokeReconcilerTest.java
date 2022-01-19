package io.quarkiverse.operatorsdk.samples.joke;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;
import io.quarkiverse.operatorsdk.samples.joke.JokeRequestSpec.Category;
import io.quarkiverse.operatorsdk.samples.joke.JokeRequestStatus.State;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.quarkus.test.kubernetes.client.KubernetesTestServer;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;

@WithKubernetesTestServer
@QuarkusTest
class AlternativeJokeReconcilerTest {

    @InjectMock
    @RestClient
    JokeService jokeService;

    @KubernetesTestServer
    KubernetesServer mockServer;

    @Inject
    JokeReconciler reconciler;

    @Test
    void canReconcile() {

        // arrange
        final JokeModel joke = new JokeModel();
        joke.id = 1;
        joke.joke = "Hello";
        joke.flags = Map.of();

        Mockito.when(jokeService.getRandom(eq(Category.Any), anyString(), anyBoolean(), anyString()))
                .thenReturn(joke);

        final JokeRequest testRequest = new JokeRequest();
        testRequest.setMetadata(new ObjectMetaBuilder()
                .withName("myjoke1")
                .withNamespace(mockServer.getClient().getNamespace())
                .build());
        testRequest.setSpec(new JokeRequestSpec());
        testRequest.getSpec().setCategory(Category.Any);

        // act
        final var result = reconciler.reconcile(testRequest, new Context() {
            @Override
            public Optional<RetryInfo> getRetryInfo() {
                return Optional.empty();
            }

            @Override
            public <T> Optional<T> getSecondaryResource(Class<T> aClass, String s) {
                return Optional.empty();
            }
        });

        // assert
        assertThat(result.getResource().getStatus().getState(), equalTo(State.CREATED));

        var createdJokes = mockServer.getClient().resources(Joke.class)
                .inNamespace(testRequest.getMetadata().getNamespace())
                .list();
        assertThat(createdJokes.getItems(), is(not(empty())));
        assertThat(createdJokes.getItems().get(0).getJoke(), is("Hello"));
    }

}
