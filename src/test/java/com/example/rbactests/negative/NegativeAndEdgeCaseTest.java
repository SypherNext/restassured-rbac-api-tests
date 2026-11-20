package com.example.rbactests.negative;

import com.example.rbactests.support.ApiSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * "What happens with bad input?" -- missing fields, wrong ids, wrong
 * types, boundary values. Nothing here is guessed: every expected status
 * and error body was confirmed against the real, running app.
 */
class NegativeAndEdgeCaseTest {

    @ParameterizedTest(name = "GET /api/appointments/{0} returns 404")
    @ValueSource(ints = {0, -1, 999999, 999999999})
    void nonexistentAppointmentIdsAllReturn404(int id) {
        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
        .when()
            .get("/api/appointments/" + id)
        .then()
            .statusCode(404);
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

    @Test
    void omittingTheDocumentedlyRequiredReasonFieldIsNowRejected() {
        // The spec marks "reason" as required. The route used to be lenient
        // and accept the request anyway with reason stored as null; it now
        // actually enforces the required fields the spec documents.
        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
            .body("""
                    { "patient_id": %d, "doctor_id": %d, "appointment_date": "2026-09-15T10:00:00" }
                    """.formatted(ApiSpec.userIdFor(ApiSpec.Role.PATIENT), ApiSpec.userIdFor(ApiSpec.Role.DOCTOR)))
        .when()
            .post("/api/appointments")
        .then()
            .statusCode(400);
    }

    @Test
    void aNonNumericPatientIdIsNowRejectedUpFront() {
        // Used to be a real bug: a non-numeric patient_id came back as 201
        // with a brand-new "appointmentId" that was never actually
        // persisted -- the server reported success for a write that didn't
        // really happen. Fixed: patient_id is now validated as an integer,
        // and checked against a real Patient user, before any insert runs.
        String reason = "phantom-success-probe-" + System.currentTimeMillis();

        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
            .body("""
                    {
                      "patient_id": "not-a-number",
                      "doctor_id": %d,
                      "appointment_date": "2026-09-15T10:00:00",
                      "reason": "%s"
                    }
                    """.formatted(ApiSpec.userIdFor(ApiSpec.Role.DOCTOR), reason))
        .when()
            .post("/api/appointments")
        .then()
            .statusCode(400);
    }

    @Test
    void omittingAllRequiredNumericFieldsNowReturnsACleanBadRequest() {
        // Used to be a real gap: there was no input validation here at
        // all, so a body missing patient_id/doctor_id/appointment_date blew
        // up as an unhandled 500. Fixed: missing required fields are now
        // rejected with a clean 400 before the query ever runs.
        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
            .body("{}")
        .when()
            .post("/api/appointments")
        .then()
            .statusCode(400);
    }

    @Test
    void registeringWithAnAlreadyTakenUsernameIsRejected() {
        String existingUsername = ApiSpec.credentialsFor(ApiSpec.Role.ADMINISTRATOR)[0];

        given()
            .spec(ApiSpec.base())
            .body("""
                    {
                      "username": "%s",
                      "email": "someone-else@example.com",
                      "password": "abc12345",
                      "role": "Patient",
                      "full_name": "Duplicate Username"
                    }
                    """.formatted(existingUsername))
        .when()
            .post("/api/auth/register")
        .then()
            .statusCode(409);
    }

    @Test
    void loggingInWithANonexistentUsernameReturns401NotSomethingElse() {
        given()
            .spec(ApiSpec.base())
            .body("""
                    { "username": "this_user_does_not_exist_at_all", "password": "whatever123" }
                    """)
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(401)
            .body("error", equalTo("Invalid credentials"));
    }

    @Test
    void aPatientCannotReadAnotherPatientsAppointmentById() {
        int patient1Id = ApiSpec.userIdFor(ApiSpec.Role.PATIENT);
        int someoneElsesAppointmentId = ApiSpec.anyAppointmentIdForPatientOtherThan(patient1Id);

        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.PATIENT))
        .when()
            .get("/api/appointments/" + someoneElsesAppointmentId)
        .then()
            .statusCode(403)
            .body("error", equalTo("Access denied"));
    }

    @Test
    void aPatientCannotReadAnotherPatientsRecordsByPatientId() {
        int patient1Id = ApiSpec.userIdFor(ApiSpec.Role.PATIENT);
        int someoneElsesPatientId = ApiSpec.anyPatientIdOtherThan(patient1Id);

        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.PATIENT))
        .when()
            .get("/api/patients/" + someoneElsesPatientId + "/records")
        .then()
            .statusCode(403);
    }
}
