package com.example.rbactests.schema;

import com.example.rbactests.support.ApiSpec;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;

/**
 * Whole-response-shape contract validation: one assertion checks every
 * field's presence, type, and (where relevant) enum constraint at once,
 * instead of a growing list of individual body("field", ...) checks.
 */
class SchemaValidationTest {

    @Test
    void appointmentResponseMatchesItsSchema() {
        // Create our own appointment rather than assuming a specific seed
        // id exists -- this app's ids aren't guaranteed stable across reseeds.
        int appointmentId =
                given()
                    .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
                    .body("""
                            { "patient_id": %d, "doctor_id": %d, "appointment_date": "2026-09-15T10:00:00", "reason": "schema validation probe" }
                            """.formatted(ApiSpec.userIdFor(ApiSpec.Role.PATIENT), ApiSpec.userIdFor(ApiSpec.Role.DOCTOR)))
                .when()
                    .post("/api/appointments")
                .then()
                    .statusCode(201)
                .extract()
                    .path("appointmentId");

        try {
            given()
                .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
            .when()
                .get("/api/appointments/" + appointmentId)
            .then()
                .statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/appointment-schema.json"));
        } finally {
            given()
                .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
            .when()
                .delete("/api/appointments/" + appointmentId);
        }
    }

    @Test
    void medicalRecordResponseMatchesItsSchema() {
        int recordId =
                given()
                    .spec(ApiSpec.authenticatedAs(ApiSpec.Role.DOCTOR))
                    .body("""
                            { "patient_id": %d, "diagnosis": "schema validation test" }
                            """.formatted(ApiSpec.userIdFor(ApiSpec.Role.PATIENT)))
                .when()
                    .post("/api/medical-records")
                .then()
                    .statusCode(201)
                .extract()
                    .path("recordId");

        try {
            given()
                .spec(ApiSpec.authenticatedAs(ApiSpec.Role.DOCTOR))
            .when()
                .get("/api/medical-records/" + recordId)
            .then()
                .statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/medical-record-schema.json"));
        } finally {
            given()
                .spec(ApiSpec.authenticatedAs(ApiSpec.Role.DOCTOR))
            .when()
                .delete("/api/medical-records/" + recordId);
        }
    }

    @Test
    void patientsListResponseMatchesItsArraySchema() {
        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
        .when()
            .get("/api/patients")
        .then()
            .statusCode(200)
            .body(matchesJsonSchemaInClasspath("schemas/patients-list-schema.json"));
    }

    @Test
    void userResponseMatchesItsSchema() {
        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
        .when()
            .get("/api/users/" + ApiSpec.userIdFor(ApiSpec.Role.PATIENT))
        .then()
            .statusCode(200)
            .body(matchesJsonSchemaInClasspath("schemas/user-schema.json"));
    }
}
