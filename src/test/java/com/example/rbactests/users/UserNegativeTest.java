package com.example.rbactests.users;

import com.example.rbactests.support.ApiSpec;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

class UserNegativeTest {

    @Test
    void noTokenReturns401() {
        given()
            .spec(ApiSpec.base())
        .when()
            .get("/api/users")
        .then()
            .statusCode(401);
    }

    @Test
    void aNonexistentUserIdReturns404ForAnAdministrator() {
        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
        .when()
            .get("/api/users/999999")
        .then()
            .statusCode(404)
            .body("error", equalTo("User not found"));
    }
}
