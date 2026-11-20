package com.example.rbactests.users;

import com.example.rbactests.support.ApiSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * PUT /api/users/{id} with a real read-after-write check -- the same
 * discipline the CRUD module insisted on. Captures patient1's real
 * current values before each test and restores them afterward, so
 * repeated runs never drift the seed account regardless of what this
 * app's seed script happens to set them to.
 */
class UserUpdateTest {

    private int patient1Id;
    private String originalFullName;
    private String originalPhone;
    private String originalEmail;

    @BeforeEach
    void captureOriginalValues() {
        patient1Id = ApiSpec.userIdFor(ApiSpec.Role.PATIENT);

        var original =
                given()
                    .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
                .when()
                    .get("/api/users/" + patient1Id)
                .then()
                    .statusCode(200)
                .extract()
                    .response();

        originalFullName = original.jsonPath().getString("full_name");
        originalPhone = original.jsonPath().getString("phone");
        originalEmail = original.jsonPath().getString("email");
    }

    @AfterEach
    void restorePatient1() {
        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
            .body("""
                    { "full_name": "%s", "phone": "%s", "email": "%s" }
                    """.formatted(originalFullName, originalPhone, originalEmail))
        .when()
            .put("/api/users/" + patient1Id);
    }

    @Test
    void updatingAProfileActuallyPersists() {
        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.PATIENT))
            .body("""
                    { "full_name": "Updated Name", "phone": "555-0000", "email": "updated@example.com" }
                    """)
        .when()
            .put("/api/users/" + patient1Id)
        .then()
            .statusCode(200);

        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.PATIENT))
        .when()
            .get("/api/users/" + patient1Id)
        .then()
            .statusCode(200)
            .body("full_name", equalTo("Updated Name"))
            .body("phone", equalTo("555-0000"));
    }
}
