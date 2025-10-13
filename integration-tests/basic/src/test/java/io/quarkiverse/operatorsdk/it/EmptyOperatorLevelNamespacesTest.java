package io.quarkiverse.operatorsdk.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(EmptyOperatorLevelNamespacesTestProfile.class)
public class EmptyOperatorLevelNamespacesTest {

    @Test
    public void reconcilersWithoutSpecificNamespacesShouldWatchAllNamespaces() {
        given()
                .when().get("/operator/" + TestReconciler.NAME +
                        "/config")
                .then()
                .statusCode(200)
                .body("watchAllNamespaces", is(true));
    }

    @Test
    public void reconcilerWithSpecificNamespacesShouldUseThem() {
        given()
                .when().get("/operator/" + ApplicationScopedReconciler.NAME +
                        "/config")
                .then()
                .statusCode(200)
                .body("watchAllNamespaces", is(false))
                .body("namespaces.size()", is(1))
                .body("namespaces[0]", equalTo("default"));
    }
}
