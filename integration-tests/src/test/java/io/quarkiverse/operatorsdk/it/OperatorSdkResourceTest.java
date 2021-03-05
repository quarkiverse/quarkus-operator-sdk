package io.quarkiverse.operatorsdk.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class OperatorSdkResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
            .when().get("/operator-sdk")
            .then()
            .statusCode(200)
            .body(is("Hello operator-sdk"));
    }
}
