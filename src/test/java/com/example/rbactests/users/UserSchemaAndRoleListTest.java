package com.example.rbactests.users;

import com.example.rbactests.support.ApiSpec;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;

/**
 * Capstone finish: schema validation on /api/users/{id}, and confirming
 * GET /api/users/role/{role} really is open to every role with no
 * restriction at all -- the simplest possible "test" here is just
 * confirming that stays true.
 */
class UserSchemaAndRoleListTest {

    @org.junit.jupiter.api.Test
    void userResponseMatchesItsSchema() {
        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
        .when()
            .get("/api/users/" + ApiSpec.userIdFor(ApiSpec.Role.PATIENT))
        .then()
            .statusCode(200)
            .body(matchesJsonSchemaInClasspath("schemas/user-schema.json"));
    }

    @ParameterizedTest(name = "{0} can list users by role, no restriction")
    @EnumSource(ApiSpec.Role.class)
    void usersByRoleIsOpenToEveryAuthenticatedRole(ApiSpec.Role role) {
        given()
            .spec(ApiSpec.authenticatedAs(role))
        .when()
            .get("/api/users/role/Doctor")
        .then()
            .statusCode(200);
    }
}
