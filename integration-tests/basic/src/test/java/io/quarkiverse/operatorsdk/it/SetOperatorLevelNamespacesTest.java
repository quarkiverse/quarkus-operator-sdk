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
                .body("customResourceClass", equalTo(resourceName))
                .body("name", equalTo(TestReconciler.NAME))
                .body("watchCurrentNamespace", is(false))
                .body("namespaces", hasSize(1))
                .body("namespaces", hasItem("operator-level")) // namespace is set at the operator level by the TestProfile, so the namespace value should match what was set there
                .body("retry.maxAttempts", equalTo(1))
                .body("generationAware", equalTo(false))
                .body("maxReconciliationIntervalSeconds", equalTo(TestReconciler.INTERVAL));
    }

    @Test
    void dependentAnnotationsShouldAppearInConfiguration() {
        given()
                .when()
                .get("/operator/" + DependentDefiningReconciler.NAME + "/config")
                .then()
                .statusCode(200)
                .body("watchCurrentNamespace", Matchers.equalTo(false))
                .body("namespaces", hasSize(1))
                .body("namespaces", hasItem("operator-level"))
                .body("dependents", hasSize(2))
                .body("dependents.dependentClass",
                        hasItems(ReadOnlyDependentResource.class.getCanonicalName(),
                                CRUDDependentResource.class.getCanonicalName()))
                .body("dependents.dependentConfig.labelSelector",
                        hasItems(ReadOnlyDependentResource.LABEL_SELECTOR, CRUDDependentResource.LABEL_SELECTOR))
                .body("dependents.dependentConfig.onAddFilter",
                        hasItem(CRUDDependentResource.TestOnAddFilter.class.getCanonicalName()));
    }
}
