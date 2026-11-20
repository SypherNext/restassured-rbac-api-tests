package com.example.rbactests.smoke;

import com.example.rbactests.support.ApiSpec;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.lessThan;

/**
 * Fast first check: is the app even up and basically working? Run this
 * before the full suite -- a red result here means stop, the rest of the
 * suite isn't worth running against a backend that can't even log in.
 */
class SmokeTest {

    @Test
    void theServerIsUp() {
        given()
            .spec(ApiSpec.base())
        .when()
            .get("/api-docs")
        .then()
            .statusCode(200);
    }

    @Test
    void loginWorksForTheAdminAccount() {
        given()
            .spec(ApiSpec.base())
            .body("""
                    { "username": "admin", "password": "admin123" }
                    """)
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(200);
    }

    @Test
    void aCoreProtectedEndpointRespondsForAnAuthenticatedUser() {
        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
        .when()
            .get("/api/appointments")
        .then()
            .statusCode(200);
    }

    @Test
    void doctorCanReachThePatientList() {
        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.DOCTOR))
        .when()
            .get("/api/patients")
        .then()
            .statusCode(200)
            .time(lessThan(2000L), TimeUnit.MILLISECONDS);
    }

    @Test
    void loginRespondsReasonablyFast() {
        given()
            .spec(ApiSpec.base())
            .body("""
                    { "username": "admin", "password": "admin123" }
                    """)
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .time(lessThan(2000L), TimeUnit.MILLISECONDS);
    }
}
