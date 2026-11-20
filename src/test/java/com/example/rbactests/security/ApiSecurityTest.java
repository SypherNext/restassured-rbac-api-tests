package com.example.rbactests.security;

import com.example.rbactests.support.ApiSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Broken authentication, injection, and broken authorization -- the three
 * categories this suite deliberately probes. One of them (mass
 * assignment on registration) is a real, serious, reproducible finding
 * against this app; this test proves it precisely rather than leaving it
 * as a suspicion. Marked @Isolated because it creates a brand-new, fully
 * privileged account and shouldn't run concurrently with anything else
 * that enumerates all users.
 */
@Isolated
class ApiSecurityTest {

    @Test
    void sqlInjectionStyleUsernameDoesNotBypassLogin() {
        given()
            .spec(ApiSpec.base())
            .body("""
                    { "username": "admin' OR '1'='1", "password": "anything" }
                    """)
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(401)
            .body("error", equalTo("Invalid credentials"));
    }

    @Test
    void anyoneCanRegisterThemselvesAsAnAdministratorMassAssignmentFinding() {
        String username = "security_test_admin_" + System.currentTimeMillis();

        given()
            .spec(ApiSpec.base())
            .body("""
                    {
                      "username": "%s",
                      "email": "%s@example.com",
                      "password": "abc12345",
                      "role": "Administrator",
                      "full_name": "Security Test"
                    }
                    """.formatted(username, username))
        .when()
            .post("/api/auth/register")
        .then()
            .statusCode(201);

        String token =
                given()
                    .spec(ApiSpec.base())
                    .body("""
                            { "username": "%s", "password": "abc12345" }
                            """.formatted(username))
                .when()
                    .post("/api/auth/login")
                .then()
                    .statusCode(200)
                    .body("user.role", equalTo("Administrator")) // fully self-granted
                .extract()
                    .path("token");

        var usersResponse =
                given()
                    .spec(ApiSpec.base())
                    .header("Authorization", "Bearer " + token)
                .when()
                    .get("/api/users") // Administrator-only endpoint
                .then()
                    .statusCode(200) // succeeds -- self-registered account has full admin access
                .extract()
                    .response();

        java.util.List<String> usernames = usersResponse.jsonPath().getList("username", String.class);
        java.util.List<Integer> ids = usersResponse.jsonPath().getList("id", Integer.class);
        int indexOfNewUser = usernames.indexOf(username);

        // Clean up the account this test created, using a real admin.
        if (indexOfNewUser >= 0) {
            int idToDelete = ids.get(indexOfNewUser);
            given()
                .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
            .when()
                .delete("/api/users/" + idToDelete);
        }
    }

    @Test
    void attemptingToSmuggleARoleChangeThroughUserUpdateIsIgnored() {
        int patient1Id = ApiSpec.userIdFor(ApiSpec.Role.PATIENT);

        // Capture patient1's real current values first, so this test can
        // restore them exactly afterward instead of guessing what they are.
        var original =
                given()
                    .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
                .when()
                    .get("/api/users/" + patient1Id)
                .then()
                    .statusCode(200)
                .extract()
                    .response();
        String originalFullName = original.jsonPath().getString("full_name");
        String originalPhone = original.jsonPath().getString("phone");
        String originalEmail = original.jsonPath().getString("email");

        try {
            // Real, verified quirk this test has to work around: PUT
            // /api/users/{id} returns a 500 unless full_name, phone, AND
            // email are ALL present in the same request, even though the
            // spec marks each of the three as independently optional -- a
            // partial update isn't actually supported. So this still sends
            // all three, with "role" smuggled in alongside them.
            given()
                .spec(ApiSpec.authenticatedAs(ApiSpec.Role.PATIENT))
                .body("""
                        {
                          "full_name": "Still Just A Patient",
                          "phone": "%s",
                          "email": "%s",
                          "role": "Administrator"
                        }
                        """.formatted(originalPhone, originalEmail))
            .when()
                .put("/api/users/" + patient1Id)
            .then()
                .statusCode(200);

            given()
                .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
            .when()
                .get("/api/users/" + patient1Id)
            .then()
                .statusCode(200)
                .body("role", equalTo("Patient")); // unchanged -- extra field ignored
        } finally {
            given()
                .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
                .body("""
                        { "full_name": "%s", "phone": "%s", "email": "%s" }
                        """.formatted(originalFullName, originalPhone, originalEmail))
            .when()
                .put("/api/users/" + patient1Id);
        }
    }

    @Test
    void updatingAUserWithOnlyOneOfTheThreeOptionalFieldsNowWorks() {
        // Used to be a real gap: full_name, phone, and email are each
        // documented as independently optional on this endpoint, but
        // sending only one of them (a legitimate partial update) returned
        // an unhandled 500 instead of a 200 -- all three had to be sent
        // together for the endpoint to work at all. Fixed: the update now
        // only touches the fields actually provided.
        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.PATIENT))
            .body("""
                    { "phone": "555-0000" }
                    """)
        .when()
            .put("/api/users/" + ApiSpec.userIdFor(ApiSpec.Role.PATIENT))
        .then()
            .statusCode(200);
    }

    @Test
    void patientCannotReadAnotherPatientsAppointmentById() {
        int otherAppointmentId = ApiSpec.anyAppointmentIdForPatientOtherThan(ApiSpec.userIdFor(ApiSpec.Role.PATIENT));

        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.PATIENT))
        .when()
            .get("/api/appointments/" + otherAppointmentId)
        .then()
            .statusCode(403)
            .body("error", equalTo("Access denied"));
    }

    @Test
    void patientCannotDeleteAnotherPatientsAppointment() {
        int otherAppointmentId = ApiSpec.anyAppointmentIdForPatientOtherThan(ApiSpec.userIdFor(ApiSpec.Role.PATIENT));

        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.PATIENT))
        .when()
            .delete("/api/appointments/" + otherAppointmentId)
        .then()
            .statusCode(403);
    }
}
