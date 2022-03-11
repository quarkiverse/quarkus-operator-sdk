package io.quarkiverse.operatorsdk.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(CustomKubernetesServerTestResource.class)
class OperatorSDKResourceTest {

    @Test
    void shouldNotValidateCRs() {
        given().when().get("/operator/config").then().statusCode(200).body(
                "validate", equalTo(false));
    }

    @Test
    void shouldNotApplyCRDsByDefault() {
        given().when().get("/operator/config").then().statusCode(200).body(
                "applyCRDs", equalTo(false));
    }

    @Test
    void shouldHavePropertiesDefinedReconciliationThreads() {
        given().when().get("/operator/config").then().statusCode(200).body(
                "maxThreads", equalTo(10));
    }

    @Test
    void shouldHavePropertiesDefinedTerminationTimeout() {
        given().when().get("/operator/config").then().statusCode(200).body(
                "timeout", equalTo(20));
    }

    @Test
    void controllerShouldExist() {
        // first check that we're not always returning true for any controller name :)
        given().when().get("/operator/does_not_exist").then().statusCode(200).body(is("false"));

        // given the name of the TestController, the app should reply true meaning that it is indeed
        // injected
        given().when().get("/operator/" + TestReconciler.NAME).then().statusCode(200)
                .body(is("true"));
    }

    @Test
    void allControllersShouldHaveAssociatedConfiguration() {
        final var names = given().when().get("/operator/controllers").then()
                .statusCode(200)
                .contentType("application/json")
                .extract()
                .as(String[].class);
        assertThat(names, arrayContainingInAnyOrder(ApplicationScopedReconciler.NAME, ConfiguredReconciler.NAME,
                DelayedReconciler.NAME, TestReconciler.NAME,
                SecretReconciler.class.getSimpleName().toLowerCase(Locale.ROOT),
                GatewayReconciler.class.getSimpleName().toLowerCase(Locale.ROOT),
                DependentDefiningReconciler.NAME));
    }

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
                        "useFinalizer", equalTo(false),
                        "watchCurrentNamespace", equalTo(true),
                        "generationAware", equalTo(false));
    }

    @Test
    void applicationPropertiesShouldOverrideDefaultAndAnnotation() {
        given()
                .when()
                .get("/operator/" + ConfiguredReconciler.NAME + "/config")
                .then()
                .statusCode(200)
                .body(
                        "finalizer", equalTo("from-property/finalizer"),
                        "namespaces", hasItem("bar"),
                        "retryConfiguration.maxAttempts", equalTo(10),
                        "retryConfiguration.initialInterval", equalTo(20000),
                        "labelSelector", equalTo("environment=production,tier!=frontend"));

        given()
                .when()
                .get("/operator/" + ApplicationScopedReconciler.NAME + "/config")
                .then()
                .statusCode(200)
                .body("namespaces", hasItem("default"));
    }

    @Test
    void delayedControllerShouldWaitForEventToRegister() {
        // first check that the delayed controller is not registered, though it should be a known controller
        given().when().get("/operator/registered/" + DelayedReconciler.NAME).then().statusCode(200).body(is("false"));
        given().when().get("/operator/" + DelayedReconciler.NAME).then().statusCode(200).body(is("true"));
        given()
                .when()
                .get("/operator/" + DelayedReconciler.NAME + "/config")
                .then()
                .statusCode(200)
                .body("delayed", equalTo(true));

        // call the register endpoint to trigger the event that the DelayedController is waiting for
        given().when().post("/operator/register").then().statusCode(204);

        // and check that the controller is now registered
        given().when().get("/operator/registered/" + DelayedReconciler.NAME).then().statusCode(200).body(is("true"));
    }

    @Test
    void dependentAnnotationsShouldAppearInConfiguration() {
        final var config = given()
                .when()
                .get("/operator/" + DependentDefiningReconciler.NAME + "/config")
                .then()
                .statusCode(200).body(
                        "dependents", hasSize(2),
                        "dependents.dependentClass", hasItems(ReadOnlyDependentResource.class.getCanonicalName(),
                                CRUDDependentResource.class.getCanonicalName()),
                        "dependents.dependentConfig.labelSelector", hasItem(CRUDDependentResource.LABEL_SELECTOR));
    }
}
