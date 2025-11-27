package io.quarkiverse.operatorsdk.it.cdi;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Verify that the Condition subclasses are properly recognized as CDI beans.
 * </p>
 * The test sets up a CR with two dependents - Deployment and ConfigMap. Deployment
 * dependent sets the readyPostcondition, activationCondition, and reconcilePrecondition conditions
 * which are all evaluated when the CR and thus the Deployment dependent are created. The
 * ConfigMap then defines only the deletePostcondition condition, but it depends on the
 * Deployment dependent which is evaluated when the CR and the Deployment is deleted.
 * </p>
 * All conditions are defined as CDI beans and inject an instance of {@link TestUUIDBean}. When their
 * {@link io.javaoperatorsdk.operator.processing.dependent.workflow.Condition#isMet(DependentResource, HasMetadata, Context)}
 * method is invoked, they save the random UUID produced by the bean to be verified in this test.
 * </p>
 * The test creates a new CR and verifies that all of readyPostcondition, activationCondition,
 * and reconcilePrecondition conditions correctly injected the CDI bean. Then it deletes the CR
 * to check the last deletePostcondition in the same manner.
 */
@QuarkusTest
class ConditionsCDITest {

    private static final Duration timeout = Duration.ofSeconds(30);

    @Inject
    KubernetesClient kubernetesClient;

    @Inject
    TestUUIDBean testUUIDBean;

    @Inject
    ReadyPostCondition readyPostCondition;

    @Inject
    CustomActionvationCondition customActionvationCondition;

    @Inject
    ReconcilePrecondition reconcilePrecondition;

    @Inject
    DeletePostCondition deletePostCondition;

    @AfterEach
    void cleanup() {
        kubernetesClient.apiextensions().v1().customResourceDefinitions().withName("testresources.josdk.quarkiverse.io")
                .delete();
    }

    @Test
    void conditionsInjectionsTest() {
        String expectedUUID = testUUIDBean.uuid();

        TestResource testResource = createTestResource();
        kubernetesClient.resource(testResource).create();

        // All conditions should be ready after ReadyPostCondition is evaluated, so waiting for it is enough
        await().atMost(timeout)
                .untilAsserted(() -> assertNotNull(readyPostCondition.getUuid()));

        assertEquals(expectedUUID, readyPostCondition.getUuid(), "ReadyPostCondition injection not processed");
        assertEquals(expectedUUID, customActionvationCondition.getUuid(), "CustomActivationCondition injection not processed");
        assertEquals(expectedUUID, reconcilePrecondition.getUuid(), "ReconcilePrecondition injection not processed");

        kubernetesClient.resource(testResource).delete();

        await().atMost(timeout)
                .untilAsserted(() -> assertEquals(expectedUUID, deletePostCondition.getUuid(),
                        "DeletePostCondition injection not processed"));

    }

    private static TestResource createTestResource() {
        var tr = new TestResource();
        tr.setMetadata(new ObjectMetaBuilder()
                .withName("test-resource-sample").build());
        return tr;
    }
}
