package io.quarkiverse.operatorsdk.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.DisabledOnIntegrationTest;
import io.quarkus.test.junit.QuarkusTest;

/**
 * This test will only pass in IDEs if you set your runner to set env properties as follow:
 *
 * <ul>
 * <li>{@link NamespaceFromEnvReconciler#ENV_VAR_NAME} = {@link NamespaceFromEnvReconciler#FROM_ENV_VAR_NS}</li>
 * <li>QUARKUS_OPERATOR_SDK_CONTROLLERS_{@link EmptyReconciler#NAME}.toUpperCase()_NAMESPACES =
 * {@link EmptyReconciler#FROM_ENV_NS1} + ", " + {@link EmptyReconciler#FROM_ENV_NS2}</li>
 * <li>{@link VariableNSReconciler#ENV_VAR_NAME} = {@link VariableNSReconciler#EXPECTED_NS_VALUE}</li>
 * <li>QUARKUS_OPERATOR_SDK_CONTROLLERS_ReconcilerUtils.getDefaultNameFor(KeycloakController.class).toUpperCase()_NAMESPACES =
 * {@link KeycloakController#FROM_ENV}</li>
 * </ul>
 *
 * See also {@code maven-surefire-plugin} configuration where these same environment variables are set
 */
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
    void shouldHaveCustomMetricsImplementationIfDefined() {
        given().when().get("/operator/config").then().statusCode(200).body(
                "registryBound", equalTo(true));
    }

    @Test
    void shouldOnlyHaveLeaderElectionActivatedInRequestedModes() {
        given().when().get("/operator/config").then().statusCode(200).body(
                "leaderConfig", equalTo(TestLeaderElectionConfiguration.class.getName()));
    }

    @Test
    void shouldBeAbleToConfigureSSASupportFromProperties() {
        given().when().get("/operator/config").then().statusCode(200).body(
                "useSSA", equalTo(false));
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
                ReconcilerUtils.getDefaultNameFor(SecretReconciler.class),
                ReconcilerUtils.getDefaultNameFor(GatewayReconciler.class),
                DependentDefiningReconciler.NAME, NamespaceFromEnvReconciler.NAME,
                EmptyReconciler.NAME, VariableNSReconciler.NAME,
                AnnotatedDependentReconciler.NAME,
                ReconcilerUtils.getDefaultNameFor(KeycloakController.class),
                NameWithSpaceReconciler.NAME));
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
                        "watchCurrentNamespace", equalTo(false),
                        "namespaces", hasSize(1),
                        "namespaces", hasItem("operator-level"),
                        "retry.maxAttempts", equalTo(1),
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

        given()
                .when()
                .get("/operator/" + NameWithSpaceReconciler.NAME + "/config")
                .then()
                .statusCode(200)
                .body("namespaces", hasItem("name-with-space"));
    }

    @Test
    void dependentAnnotationsShouldAppearInConfiguration() {
        given()
                .when()
                .get("/operator/" + DependentDefiningReconciler.NAME + "/config")
                .then()
                .statusCode(200).body(
                        "watchCurrentNamespace", equalTo(false),
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

    @Test
    void workflowShouldBeRetrievable() {
        given()
                .when()
                .get("/operator/" + DependentDefiningReconciler.NAME + "/workflow")
                .then()
                .statusCode(200).body(
                        "cleaner", is(false),
                        "empty", is(false),
                        "dependents." + ReadOnlyDependentResource.NAME + ".type",
                        startsWith(ReadOnlyDependentResource.class.getName()),
                        "dependents." + ReadOnlyDependentResource.NAME + ".readyCondition",
                        startsWith(ReadOnlyDependentResource.ReadOnlyReadyCondition.class.getName()),
                        "dependents.crud.type", startsWith(CRUDDependentResource.class.getName()));
    }

    @Test
    void dependentConfigurationShouldBeRetrievableAfterConfiguration() {
        given()
                .when()
                .get("/operator/" + DependentDefiningReconciler.NAME + "/dependents/" + ReadOnlyDependentResource.NAME)
                .then()
                .statusCode(200).body(
                        "resourceDiscriminator",
                        is(ReadOnlyDependentResource.ReadOnlyResourceDiscriminator.class.getCanonicalName()));
    }

    @Test
    void shouldExpandVariablesInNamespacesConfigurationFromAnnotation() {
        assertThat(System.getenv(NamespaceFromEnvReconciler.ENV_VAR_NAME),
                is(NamespaceFromEnvReconciler.FROM_ENV_VAR_NS));
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
        assertThat(System.getenv(VariableNSReconciler.ENV_VAR_NAME), is(VariableNSReconciler.EXPECTED_NS_VALUE));
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

        given()
                .when()
                .get("/operator/" + ReconcilerUtils.getDefaultNameFor(KeycloakController.class) + "/config")
                .then()
                .statusCode(200)
                .body(
                        "namespaces", hasItem(KeycloakController.FROM_ENV),
                        "watchCurrentNamespace", equalTo(false),
                        "namespaces", hasSize(1));
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
