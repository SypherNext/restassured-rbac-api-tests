package com.example.rbactests.users;

import com.example.rbactests.support.ApiSpec;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * GET /api/users/{id}'s real rule isn't a flat role check: it's
 * "yourself, or an Administrator" -- an ownership rule, which needs its
 * own tests rather than a role table. Ids are resolved live via
 * ApiSpec.userIdFor(...) rather than hardcoded.
 */
class UserOwnershipTest {

    @Test
    void aPatientCanViewTheirOwnProfile() {
        int patient1Id = ApiSpec.userIdFor(ApiSpec.Role.PATIENT);

        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.PATIENT))
        .when()
            .get("/api/users/" + patient1Id)
        .then()
            .statusCode(200)
            .body("id", equalTo(patient1Id))
            .body("username", equalTo("patient1"));
    }

    @Test
    void aPatientCannotViewSomeoneElsesProfile() {
        int doctor1Id = ApiSpec.userIdFor(ApiSpec.Role.DOCTOR);

        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.PATIENT))
        .when()
            .get("/api/users/" + doctor1Id) // doctor1's id, not patient1's
        .then()
            .statusCode(403)
            .body("error", equalTo("Access denied"));
    }

    @Test
    void anAdministratorCanViewAnyonesProfile() {
        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
        .when()
            .get("/api/users/" + ApiSpec.userIdFor(ApiSpec.Role.PATIENT))
        .then()
            .statusCode(200);
    }

    @Test
    void anAdministratorLookingUpANonexistentIdGetsA404NotA403() {
        // Confirms the admin bypass happens before the existence check,
        // not after -- a nonexistent id still reaches "not found", not
        // "access denied", for an Administrator.
        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
        .when()
            .get("/api/users/999999")
        .then()
            .statusCode(404);
    }
}
