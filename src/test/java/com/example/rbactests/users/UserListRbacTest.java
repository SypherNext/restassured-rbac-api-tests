package com.example.rbactests.users;

import com.example.rbactests.support.ApiSpec;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static io.restassured.RestAssured.given;

/**
 * Capstone: closes the one gap the coverage module flagged -- /api/users
 * never got a full suite of its own. GET /api/users is Administrator
 * only; every other role gets 403.
 */
class UserListRbacTest {

    @ParameterizedTest(name = "{0} listing all users")
    @EnumSource(ApiSpec.Role.class)
    void listUsersIsAdministratorOnly(ApiSpec.Role role) {
        int expectedStatus = role == ApiSpec.Role.ADMINISTRATOR ? 200 : 403;

        given()
            .spec(ApiSpec.authenticatedAs(role))
        .when()
            .get("/api/users")
        .then()
            .statusCode(expectedStatus);
    }
}
