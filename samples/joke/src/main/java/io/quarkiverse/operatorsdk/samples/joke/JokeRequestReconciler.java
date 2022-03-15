/**
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
 *
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.quarkiverse.operatorsdk.samples.joke;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

import java.util.Arrays;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadata;
import io.quarkiverse.operatorsdk.samples.joke.JokeRequestSpec.ExcludedTopic;
import io.quarkiverse.operatorsdk.samples.joke.JokeRequestStatus.State;

@CSVMetadata(permissionRules = @CSVMetadata.PermissionRule(apiGroups = "samples.javaoperatorsdk.io", resources = "jokes"))
@ControllerConfiguration(namespaces = WATCH_CURRENT_NAMESPACE)
public class JokeRequestReconciler implements Reconciler<JokeRequest> {
    @Inject
    @RestClient
    JokeService jokes;

    @Inject
    KubernetesClient client;

    @Override
    public UpdateControl<JokeRequest> reconcile(JokeRequest jr, Context<JokeRequest> context) {
        final var spec = jr.getSpec();

        // if the joke has already been created, ignore
        JokeRequestStatus status = jr.getStatus();
        if (status != null && State.CREATED == status.getState()) {
            return UpdateControl.noUpdate();
        }

        try {
            final JokeModel fromApi = jokes.getRandom(spec.getCategory(),
                    String.join(",", Arrays.stream(spec.getExcluded()).map(ExcludedTopic::name).toArray(String[]::new)),
                    spec.isSafe(), "single");
            status = JokeRequestStatus.from(fromApi);
            if (!status.isError()) {
                // create the joke
                final var joke = new Joke(fromApi.id, fromApi.joke, fromApi.category, fromApi.safe,
                        fromApi.lang);

                final var flags = fromApi.flags.entrySet().stream().collect(Collectors.toMap(
                        entry -> "joke_flag_" + entry.getKey(),
                        entry -> entry.getValue().toString()));
                joke.getMetadata().setLabels(flags);

                // if we don't already have created this joke on the cluster, do so
                final var jokeResource = client.resources(Joke.class)
                        .withName("" + fromApi.id);
                final var existing = jokeResource.get();
                if (existing != null) {
                    status.setMessage("Joke " + fromApi.id + " already exists");
                    status.setState(State.ALREADY_PRESENT);
                } else {
                    final var result = jokeResource.create(joke);
                    status.setMessage("Joke " + fromApi.id + " created");
                    status.setState(State.CREATED);
                }
            }
        } catch (Exception e) {
            status = new JokeRequestStatus();
            status.setMessage("Error querying API: " + e.getMessage());
            status.setState(State.ERROR);
            status.setError(true);
        }

        jr.setStatus(status);
        return UpdateControl.updateStatus(jr);
    }
}
