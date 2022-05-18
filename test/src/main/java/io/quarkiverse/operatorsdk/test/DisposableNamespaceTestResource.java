package io.quarkiverse.operatorsdk.test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil;
import io.quarkus.test.common.QuarkusTestResourceConfigurableLifecycleManager;

public class DisposableNamespaceTestResource implements
        QuarkusTestResourceConfigurableLifecycleManager<WithDisposableNamespace> {

    private static final Logger log = LoggerFactory.getLogger(DisposableNamespaceTestResource.class);
    private String namespace;
    private KubernetesClient client;
    private List<HasMetadata> resourceFixtures = Collections.emptyList();
    private int waitAtMostSecondsForNSDeletion;
    private int waitAtMostSecondsForFixturesReadiness;
    private boolean preserveNamespaceOnError;

    @Override
    public Map<String, String> start() {
        log.info("Creating '{}' namespace", namespace);
        client.namespaces()
                .create(new NamespaceBuilder().withNewMetadata().withName(namespace).endMetadata().build());

        final var resources = client.resourceList(resourceFixtures);
        resources.accept(HasMetadata.class,
                hasMetadata -> log.info("Creating '{}' {}", hasMetadata.getMetadata().getName(), hasMetadata.getKind()));
        resources.createOrReplace();
        resources.waitUntilReady(waitAtMostSecondsForFixturesReadiness, TimeUnit.SECONDS);

        return null;
    }

    @Override
    public void stop() {
        // todo: not sure if possible to detect test failure in this context
        if (preserveNamespaceOnError /* && context.getExecutionException().isPresent() */) {
            log.info("Preserving namespace {}", namespace);
        } else {
            client.resourceList(resourceFixtures).delete();
            log.info("Deleting namespace {} and stopping operator", namespace);
            client.namespaces().withName(namespace).delete();
            if (waitAtMostSecondsForNSDeletion > 0) {
                log.info("Waiting for namespace {} to be deleted", namespace);
                Awaitility.await("namespace deleted")
                        .pollInterval(50, TimeUnit.MILLISECONDS)
                        .atMost(waitAtMostSecondsForNSDeletion, TimeUnit.SECONDS)
                        .until(() -> client.namespaces().withName(namespace).get() == null);
            }
        }
    }

    @Override
    public void inject(TestInjector testInjector) {
        testInjector.injectIntoFields(client,
                new TestInjector.AnnotatedAndMatchesType(DisposableNamespacedKubernetesClient.class, KubernetesClient.class));
    }

    @Override
    public void init(WithDisposableNamespace annotation) {
        final var namespace = annotation.namespace();
        if (namespace != null && !WithDisposableNamespace.UNSET_VALUE.equals(namespace)) {
            this.namespace = namespace;
        } else {
            this.namespace = KubernetesResourceUtil.sanitizeName("ns" + UUID.randomUUID());
        }

        client = new DefaultKubernetesClient();

        waitAtMostSecondsForNSDeletion = annotation.waitAtMostSecondsForDeletion();
        preserveNamespaceOnError = annotation.preserveOnError();
        waitAtMostSecondsForFixturesReadiness = annotation.fixturesReadinessTimeoutSeconds();

        final var fixtures = annotation.fixtures();
        if (fixtures != null) {
            resourceFixtures = new LinkedList<>();
            Arrays.stream(fixtures).forEach(fixture -> {

                // deal with factory
                final var factoryClass = fixture.factory();
                if (factoryClass != null && !FixtureFactory.class.equals(factoryClass)) {
                    try {
                        FixtureFactory factory = factoryClass.getConstructor().newInstance();
                        resourceFixtures.addAll(factory.build(this.namespace));
                    } catch (InstantiationException | NoSuchMethodException | InvocationTargetException
                            | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }

                // load from YAML if defined
                final var yamlResource = fixture.fromYAMLResource();
                if (yamlResource != null && !WithDisposableNamespace.UNSET_VALUE.equals(yamlResource)) {
                    try {
                        resourceFixtures.addAll(client.load(new FileInputStream(yamlResource)).get());
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }
}
