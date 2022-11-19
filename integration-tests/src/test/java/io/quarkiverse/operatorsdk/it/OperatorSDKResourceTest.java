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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.DisabledOnIntegrationTest;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(CustomKubernetesServerTestResource.class)
class OperatorSDKResourceTest {

    @BeforeAll
    static void setup() {

    }

    @Test
    void shouldNotValidateCRs() {
        given().when().get("/operator/config").then().statusCode(200).body(
                "validate", equalTo(false));
    }

    @Test
    @DisabledOnIntegrationTest("Skipped because native tests are run using LaunchMode.NORMAL")
    void shouldApplyCRDsByDefaultInTestMode() {
        given().when().get("/operator/config").then().statusCode(200).body(
                "applyCRDs", equalTo(true));
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
        assertThat(names, arrayContainingInAnyOrder(ApplicationScopedReconciler.NAME,
                ConfiguredReconciler.NAME,
                TestReconciler.NAME,
                SecretReconciler.class.getSimpleName().toLowerCase(Locale.ROOT),
                GatewayReconciler.class.getSimpleName().toLowerCase(Locale.ROOT),
                DependentDefiningReconciler.NAME, NamespaceFromEnvReconciler.NAME,
                EmptyReconciler.NAME, VariableNSReconciler.NAME, AnnotatedDependentReconciler.NAME));
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
                        "watchCurrentNamespace", equalTo(true),
                        "generationAware", equalTo(false),
                        "maxReconciliationIntervalSeconds", equalTo(TestReconciler.INTERVAL));
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
                        "retry.maxAttempts", equalTo(ConfiguredReconciler.MAX_ATTEMPTS),
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
    void dependentAnnotationsShouldAppearInConfiguration() {
        given()
                .when()
                .get("/operator/" + DependentDefiningReconciler.NAME + "/config")
                .then()
                .statusCode(200).body(
                        "dependents", hasSize(2),
                        "dependents.dependentClass",
                        hasItems(ReadOnlyDependentResource.class.getCanonicalName(),
                                CRUDDependentResource.class.getCanonicalName()),
                        "dependents.dependentConfig.labelSelector",
                        hasItem(CRUDDependentResource.LABEL_SELECTOR),
                        "dependents.dependentConfig.onAddFilter",
                        hasItem(CRUDDependentResource.TestOnAddFilter.class.getCanonicalName()));
    }

    @Test
    void shouldExpandVariablesInNamespacesConfigurationFromAnnotation() {
        given()
                .when()
                .get("/operator/" + NamespaceFromEnvReconciler.NAME + "/config")
                .then()
                .statusCode(200).body(
                        "namespaces", hasItem(NamespaceFromEnvReconciler.FROM_ENV_VAR_NS),
                        "namespaces", hasItem("static"),
                        "namespaces", hasSize(2));
    }

    @Test
    void shouldExpandVariablesInNamespacesConfigurationFromProperties() {
        given()
                .when()
                .get("/operator/" + VariableNSReconciler.NAME + "/config")
                .then()
                .statusCode(200).body(
                        "namespaces", hasItem(VariableNSReconciler.EXPECTED_NS_VALUE),
                        "namespaces", hasSize(1));
    }

    @Test
    void shouldUseNamespacesFromEnvVariableIfSet() {
        given()
                .when()
                .get("/operator/" + EmptyReconciler.NAME + "/config")
                .then()
                .statusCode(200)
                .body(
                        "namespaces", hasItem(EmptyReconciler.FROM_ENV_NS1),
                        "namespaces", hasItem(EmptyReconciler.FROM_ENV_NS2),
                        "watchCurrentNamespace", equalTo(false),
                        "namespaces", hasSize(2));
    }

    @Test
    void customAnnotatedDependentsShouldUseAnnotationValues() {
        given()
                .when()
                .get("/operator/" + AnnotatedDependentReconciler.NAME + "/config")
                .then()
                .statusCode(200)
                .body(
                        "dependents", hasSize(1),
                        "dependents[0].dependentClass", equalTo(AnnotatedDependentResource.class.getCanonicalName()),
                        "dependents[0].dependentConfig.value", equalTo(AnnotatedDependentResource.VALUE));
    }
}
