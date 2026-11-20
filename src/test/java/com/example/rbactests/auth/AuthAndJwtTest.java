package com.example.rbactests.auth;

import com.example.rbactests.support.ApiSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Authentication and JWT behavior: issuing tokens, the two distinct
 * failure modes (missing vs. invalid credentials), and that every
 * seeded account this suite relies on can actually log in.
 */
class AuthAndJwtTest {

    @Test
    void loginReturnsATokenAndTheRightUser() {
        var response =
                given()
                    .spec(ApiSpec.base())
                    .body("""
                            { "username": "admin", "password": "admin123" }
                            """)
                .when()
                    .post("/api/auth/login");

        response.then()
                .statusCode(200)
                .body("user.username", equalTo("admin"))
                .body("user.role", equalTo("Administrator"));

        assertTrue(response.jsonPath().getString("token").length() > 20);
    }

    @ParameterizedTest(name = "{0} logging in with the correct password succeeds")
    @CsvSource({
            "admin,     admin123",
            "doctor1,   pass@123",
            "nurse1,    pass@123",
            "patient1,  pass@123"
    })
    void eachSeededAccountCanLogIn(String username, String password) {
        given()
            .spec(ApiSpec.base())
            .body("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password))
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .body("user.username", equalTo(username));
    }

    @Test
    void wrongPasswordIsRejectedWith401() {
        given()
            .spec(ApiSpec.base())
            .body("""
                    { "username": "admin", "password": "wrong-password" }
                    """)
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(401)
            .body("error", equalTo("Invalid credentials"));
    }

    @Test
    void noTokenAtAllIsRejectedWith401() {
        given()
            .spec(ApiSpec.base())
        .when()
            .get("/api/appointments")
        .then()
            .statusCode(401)
            .body("error", equalTo("Access token required"));
    }

    @Test
    void aGarbageTokenIsRejectedWith403() {
        given()
            .spec(ApiSpec.base())
            .header("Authorization", "Bearer not-a-real-token")
        .when()
            .get("/api/appointments")
        .then()
            .statusCode(403)
            .body("error", equalTo("Invalid or expired token"));
    }

    @ParameterizedTest
    @EnumSource(ApiSpec.Role.class)
    void everyRoleCanSuccessfullyLogIn(ApiSpec.Role role) {
        String token = ApiSpec.loginAndGetToken(role);
        assertTrue(token.length() > 20);
    }
}
