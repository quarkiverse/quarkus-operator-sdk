package io.quarkiverse.operatorsdk.it.cdi;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Verify that the Condition subclasses are properly recognized as CDI beans.
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
        tr.setSpec(new TestSpec());
        return tr;
    }
}
