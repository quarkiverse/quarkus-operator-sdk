package io.quarkiverse.operatorsdk.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.KubernetesServerTestResource;

@QuarkusTest
@QuarkusTestResource(KubernetesServerTestResource.class)
public class OperatorSDKResourceTest {

    @Test
    void shouldNotValidateCRs() {
        given().when().get("/operator/config").then().statusCode(200).body(
                "validate", equalTo(false));
    }

    @Test
    void shouldHavePropertiesDefinedReconciliationThreads() {
        given().when().get("/operator/config").then().statusCode(200).body(
                "maxThreads", equalTo(10));
    }

    @Test
    void controllerShouldExist() {
        // first check that we're not always returning true for any controller name :)
        given().when().get("/operator/does_not_exist").then().statusCode(200).body(is("false"));

        // given the name of the TestController, the app should reply true meaning that it is indeed
        // injected
        given().when().get("/operator/" + TestController.NAME).then().statusCode(200)
                .body(is("true"));
    }

    @Test
    void configurationForControllerShouldExist() {
        // check that the config for the test controller can be retrieved and is conform to our
        // expectations
        final var resourceName = ChildTestResource.class.getCanonicalName();
        given()
                .when()
                .get("/operator/" + TestController.NAME + "/config")
                .then()
                .statusCode(200)
                .body(
                        "customResourceClass", equalTo(resourceName),
                        "name", equalTo(TestController.NAME));
    }

    @Test
    void applicationPropertiesShouldOverrideDefaultAndAnnotation() {
        given()
                .when()
                .get("/operator/" + ConfiguredController.NAME + "/config")
                .then()
                .statusCode(200)
                .body(
                        "finalizer", equalTo("from-property/finalizer"),
                        "namespaces", hasItem("bar"));
    }
}
