package com.example.rbactests.users;

import com.example.rbactests.support.ApiSpec;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * DELETE /api/users/{id} is Administrator only -- the exact gap the
 * coverage module flagged, closed. The admin-can-delete case creates a
 * throwaway account via registration first, so it never destroys real
 * seed data.
 */
class UserDeleteTest {

    @Test
    void nonAdminCannotDeleteAUser() {
        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.NURSE))
        .when()
            .delete("/api/users/" + ApiSpec.userIdFor(ApiSpec.Role.PATIENT))
        .then()
            .statusCode(403)
            .body("requiredRoles[0]", equalTo("Administrator"));
    }

    @Test
    void administratorCanDeleteAUser() {
        String username = "capstone_delete_target_" + System.currentTimeMillis();
        given()
            .spec(ApiSpec.base())
            .body("""
                    {
                      "username": "%s", "email": "%s@example.com", "password": "abc12345",
                      "role": "Patient", "full_name": "Delete Target"
                    }
                    """.formatted(username, username))
        .when()
            .post("/api/auth/register")
        .then()
            .statusCode(201);

        int newUserId =
                given()
                    .spec(ApiSpec.base())
                    .body("""
                            { "username": "%s", "password": "abc12345" }
                            """.formatted(username))
                .when()
                    .post("/api/auth/login")
                .then()
                .extract()
                .path("user.id");

        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
        .when()
            .delete("/api/users/" + newUserId)
        .then()
            .statusCode(200)
            .body("message", equalTo("User deleted successfully"));
    }
}
