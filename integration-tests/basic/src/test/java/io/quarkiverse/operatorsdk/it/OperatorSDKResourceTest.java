package io.quarkiverse.operatorsdk.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.quarkus.test.common.WithTestResource;
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
@WithTestResource(CustomKubernetesServerTestResource.class)
class OperatorSDKResourceTest {
    @Test
    @DisabledOnIntegrationTest("Skipped because native tests are run using LaunchMode.NORMAL")
    void shouldApplyCRDsByDefaultInTestMode() {
        given().when().get("/operator/config").then().statusCode(200).body(
                "applyCRDs", equalTo(true));
    }

    @Test
    void operatorConfigShouldConformToDifferentSetOptions() {
        given().when().get("/operator/config").then().statusCode(200)
                // should not validate CRDs per application.properties
                .body("validate", equalTo(false))
                // should use max reconciliation threads from properties
                .body("maxThreads", equalTo(10))
                // both deprecated and non-deprecated property for timeout are used, only the value from the non-deprecated version should be retained
                .body("timeout", equalTo(30))
                // should have custom metrics implementation as defined by TestMetrics bean
                .body("registryBound", equalTo(true))
                // leader election configuration specified via TestLeaderElectionConfiguration bean
                .body("leaderConfig", equalTo(TestLeaderElectionConfiguration.class.getName()))
                // SSA support deactivated in application.properties
                .body("useSSA", equalTo(false))
                // default non SSA resources specified via ConfigurationServiceCustomizer bean
                .body("nonSSAResources", hasItems("Pod", "Secret", "Deployment"));
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
                ReconcilerUtilsInternal.getDefaultNameFor(GatewayReconciler.class),
                DependentDefiningReconciler.NAME, NamespaceFromEnvReconciler.NAME,
                EmptyReconciler.NAME, VariableNSReconciler.NAME,
                AnnotatedDependentReconciler.NAME,
                ReconcilerUtilsInternal.getDefaultNameFor(KeycloakController.class),
                NameWithSpaceReconciler.NAME,
                CustomRateLimiterReconciler.NAME,
                SecretReconciler.NAME));
    }

    @Test
    void configurationForControllerShouldExistAndUseBuildTimeOperatorLevelConfigurationWhenSet() {
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
                .body("watchCurrentNamespace", equalTo(false))
                // build time values are propagated at runtime if no runtime value is specified
                .body("namespaces", hasSize(2))
                .body("namespaces", hasItem("builtime-namespace1"))
                .body("namespaces", hasItem("buildtime-ns2"))
                .body("retry.maxAttempts", equalTo(1)) // should use property even if no annotation exists
                .body("generationAware", equalTo(false))
                .body("maxReconciliationIntervalSeconds", equalTo(TestReconciler.INTERVAL));
    }

    @Test
    void applicationPropertiesShouldOverrideDefaultAndAnnotation() {
        given()
                .when()
                .get("/operator/" + ConfiguredReconciler.NAME + "/config")
                .then()
                .statusCode(200)
                .body("finalizer", equalTo("from-property/finalizer"))
                .body("namespaces", hasItem("bar"))
                .body("retry.maxAttempts", equalTo(ConfiguredReconciler.MAX_ATTEMPTS)) // from annotation
                .body("retry.initialInterval", equalTo(20000)) // annotation value should be overridden by property
                .body("rateLimiter.refreshPeriod", equalTo(60F)) // for some reason the period is reported as a float
                .body("labelSelector", equalTo("environment=production,tier!=frontend"));

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
                .statusCode(200)
                .body("watchCurrentNamespace", equalTo(false))
                .body("namespaces", hasSize(1))
                .body("namespaces", hasItem("operator-level-for-manifests"))
                .body("dependents", hasSize(2))
                .body("dependents.dependentClass",
                        hasItems(ReadOnlyDependentResource.class.getCanonicalName(),
                                CRUDDependentResource.class.getCanonicalName()))
                .body("dependents.dependentConfig.labelSelector",
                        hasItems(ReadOnlyDependentResource.LABEL_SELECTOR, CRUDDependentResource.LABEL_SELECTOR))
                .body("dependents.dependentConfig.onAddFilter",
                        hasItem(CRUDDependentResource.TestOnAddFilter.class.getCanonicalName()));
    }

    @Test
    void workflowShouldBeRetrievable() {
        given()
                .when()
                .get("/operator/" + DependentDefiningReconciler.NAME + "/workflow")
                .then()
                .statusCode(200)
                .body("cleaner", is(false))
                .body("empty", is(false))
                .body("dependents." + ReadOnlyDependentResource.NAME + ".type",
                        startsWith(ReadOnlyDependentResource.class.getName()))
                .body("dependents." + ReadOnlyDependentResource.NAME + ".readyCondition",
                        startsWith(ReadOnlyDependentResource.ReadOnlyReadyCondition.class.getName()))
                .body("dependents.crud.type", startsWith(CRUDDependentResource.class.getName()));
    }

    @Test
    void dependentConfigurationShouldBeRetrievableAfterConfiguration() {
        given()
                .when()
                .get("/operator/" + DependentDefiningReconciler.NAME + "/dependents/" + ReadOnlyDependentResource.NAME)
                .then()
                .statusCode(200)
                .body("labelSelector", equalTo(ReadOnlyDependentResource.LABEL_SELECTOR));
    }

    @Test
    void shouldExpandVariablesInNamespacesConfigurationFromAnnotation() {
        assertThat(System.getenv(NamespaceFromEnvReconciler.ENV_VAR_NAME),
                is(NamespaceFromEnvReconciler.FROM_ENV_VAR_NS));
        given()
                .when()
                .get("/operator/" + NamespaceFromEnvReconciler.NAME + "/config")
                .then()
                .statusCode(200)
                .body("namespaces", hasItem(NamespaceFromEnvReconciler.FROM_ENV_VAR_NS))
                .body("namespaces", hasItem("static"))
                .body("namespaces", hasSize(2));
    }

    @Test
    void shouldExpandVariablesInNamespacesConfigurationFromProperties() {
        assertThat(System.getenv(VariableNSReconciler.ENV_VAR_NAME), is(VariableNSReconciler.EXPECTED_NS_VALUE));
        given()
                .when()
                .get("/operator/" + VariableNSReconciler.NAME + "/config")
                .then()
                .statusCode(200)
                .body("namespaces", hasItem(VariableNSReconciler.EXPECTED_NS_VALUE))
                .body("namespaces", hasSize(1));
    }

    @Test
    void shouldUseNamespacesFromEnvVariableIfSet() {
        given()
                .when()
                .get("/operator/" + EmptyReconciler.NAME + "/config")
                .then()
                .statusCode(200)
                .body("namespaces", hasItem(EmptyReconciler.FROM_ENV_NS1))
                .body("namespaces", hasItem(EmptyReconciler.FROM_ENV_NS2))
                .body("watchCurrentNamespace", equalTo(false))
                .body("namespaces", hasSize(2));

        given()
                .when()
                .get("/operator/" + ReconcilerUtilsInternal.getDefaultNameFor(KeycloakController.class) + "/config")
                .then()
                .statusCode(200)
                .body("namespaces", hasItem(KeycloakController.FROM_ENV))
                .body("watchCurrentNamespace", equalTo(false))
                .body("namespaces", hasSize(1));
    }

    @Test
    void customAnnotatedDependentsShouldUseAnnotationValues() {
        given()
                .when()
                .get("/operator/" + AnnotatedDependentReconciler.NAME + "/config")
                .then()
                .statusCode(200)
                .body("dependents", hasSize(1))
                .body("dependents[0].dependentClass", equalTo(AnnotatedDependentResource.class.getCanonicalName()))
                .body("dependents[0].dependentConfig.value", equalTo(AnnotatedDependentResource.VALUE));
    }

    @Test
    void customRateLimiterConfiguredViaCustomAnnotationShouldWork() {
        given()
                .when()
                .get("/operator/" + CustomRateLimiterReconciler.NAME + "/config")
                .then()
                .statusCode(200)
                .body("rateLimiter.value", equalTo(42))
                .body("itemStore.name", equalTo(NullItemStore.NAME));
    }

    @Test
    void shouldHaveDefaultMaxReconciliationInterval() {
        given()
                .when()
                .get("/operator/" + EmptyReconciler.NAME + "/config")
                .then()
                .statusCode(200)
                .body("maxReconciliationIntervalSeconds", equalTo(Long.valueOf(Duration.ofHours(10).getSeconds()).intValue()));
    }

    @Test
    void shouldUseMaxReconciliationIntervalFromPropertyIfProvided() {
        given()
                .when()
                .get("/operator/" + SecretReconciler.NAME + "/config")
                .then()
                .statusCode(200)
                .body("maxReconciliationIntervalSeconds",
                        equalTo(Long.valueOf(Duration.ofMinutes(15).getSeconds()).intValue()))
                .body("fieldSelector.fields[0].path", equalTo("type"))
                .body("fieldSelector.fields[0].value", equalTo(SecretReconciler.FIELD_SELECTOR_VALUE))
                .body("fieldSelector.fields[0].negated", equalTo(false));
    }
}
