package io.quarkiverse.operatorsdk.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(SetOperatorLevelNamespacesTestProfile.class)
public class SetOperatorLevelNamespacesTest {

    @Test
    void configurationForControllerShouldExistAndUseOperatorLevelConfigurationWhenSet() {
        // check that the config for the test controller can be retrieved and is conform to our
        // expectations
        final var resourceName = io.quarkiverse.operatorsdk.it.Test.class.getCanonicalName();
        given()
                .when()
                .get("/operator/" + TestReconciler.NAME + "/config")
                .then()
                .statusCode(200)
                .body(
                        "customResourceClass", equalTo(resourceName),
                        "name", equalTo(TestReconciler.NAME),
                        "watchCurrentNamespace", is(false),
                        "namespaces", hasSize(1),
                        "namespaces", hasItem("operator-level"), // namespace is set at the operator level by the TestProfile, so the namespace value should match what was set there
                        "retry.maxAttempts", equalTo(1),
                        "generationAware", equalTo(false),
                        "maxReconciliationIntervalSeconds", equalTo(TestReconciler.INTERVAL));
    }

    @Test
    void dependentAnnotationsShouldAppearInConfiguration() {
        given()
                .when()
                .get("/operator/" + DependentDefiningReconciler.NAME + "/config")
                .then()
                .statusCode(200).body(
                        "watchCurrentNamespace", Matchers.equalTo(false),
                        "namespaces", hasSize(1),
                        "namespaces", hasItem("operator-level"),
                        "dependents", hasSize(2),
                        "dependents.dependentClass",
                        hasItems(ReadOnlyDependentResource.class.getCanonicalName(),
                                CRUDDependentResource.class.getCanonicalName()),
                        "dependents.dependentConfig.labelSelector",
                        hasItems(ReadOnlyDependentResource.LABEL_SELECTOR, CRUDDependentResource.LABEL_SELECTOR),
                        "dependents.dependentConfig.onAddFilter",
                        hasItem(CRUDDependentResource.TestOnAddFilter.class.getCanonicalName()),
                        "dependents.dependentConfig.resourceDiscriminator",
                        hasItems(ReadOnlyDependentResource.ReadOnlyResourceDiscriminator.class.getCanonicalName(),
                                CRUDDependentResource.TestResourceDiscriminator.class.getCanonicalName()));
    }
}
