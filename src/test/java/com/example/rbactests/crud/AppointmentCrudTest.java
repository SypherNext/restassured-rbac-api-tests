package com.example.rbactests.crud;

import com.example.rbactests.support.ApiSpec;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Full create -> read -> update -> delete cycle against /api/appointments.
 * Every write is followed by a read-back: a 200/201 from the write call
 * alone never proves the server actually did the thing.
 *
 * Each test creates its own appointment and deletes it again -- nothing
 * here depends on, or permanently alters, the seed data. patient1's and
 * doctor1's ids are resolved live via ApiSpec.userIdFor(...) rather than
 * hardcoded, since this app's seed script does not guarantee stable ids
 * across reseeds.
 */
class AppointmentCrudTest {

    @Test
    void createReadUpdateDeleteAnAppointment() {
        int patientId = ApiSpec.userIdFor(ApiSpec.Role.PATIENT);
        int doctorId = ApiSpec.userIdFor(ApiSpec.Role.DOCTOR);

        String requestBody = """
                {
                  "patient_id": %d,
                  "doctor_id": %d,
                  "appointment_date": "2026-09-15T10:00:00",
                  "reason": "CRUD module test"
                }
                """.formatted(patientId, doctorId);

        Response createResponse =
                given()
                    .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
                    .body(requestBody)
                .when()
                    .post("/api/appointments")
                .then()
                    .statusCode(201)
                    .body("message", equalTo("Appointment created successfully"))
                    .body("appointmentId", notNullValue())
                .extract()
                    .response();

        int appointmentId = createResponse.jsonPath().getInt("appointmentId");

        try {
            // Read: creation only returns {message, appointmentId}, never the
            // full record -- a second call is required to see what was stored.
            given()
                .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
            .when()
                .get("/api/appointments/" + appointmentId)
            .then()
                .statusCode(200)
                .body("id", equalTo(appointmentId))
                .body("patient_id", equalTo(patientId))
                .body("doctor_id", equalTo(doctorId))
                .body("status", equalTo("scheduled"))
                .body("reason", equalTo("CRUD module test"))
                .body("patient_name", notNullValue())
                .body("doctor_name", notNullValue());

            // Update
            given()
                .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
                .body("""
                        { "status": "completed", "notes": "updated via test" }
                        """)
            .when()
                .put("/api/appointments/" + appointmentId)
            .then()
                .statusCode(200)
                .body("message", equalTo("Appointment updated successfully"));

            // Confirm the update actually took, by reading it back again.
            given()
                .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
            .when()
                .get("/api/appointments/" + appointmentId)
            .then()
                .statusCode(200)
                .body("status", equalTo("completed"))
                .body("notes", equalTo("updated via test"));

        } finally {
            // Delete
            given()
                .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
            .when()
                .delete("/api/appointments/" + appointmentId)
            .then()
                .statusCode(200)
                .body("message", equalTo("Appointment deleted successfully"));

            // Confirm it's actually gone.
            given()
                .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
            .when()
                .get("/api/appointments/" + appointmentId)
            .then()
                .statusCode(404);
        }
    }

    @Test
    void creatingAnAppointmentHasNoRoleGateAtAll() {
        // Real, verified divergence: the spec/docs imply only certain roles
        // should create appointments, but the endpoint accepts ANY
        // authenticated role -- including Patient, creating one for someone
        // else entirely. This test documents the actual behavior, not a bug
        // this suite is trying to hide.
        int patientId = ApiSpec.userIdFor(ApiSpec.Role.PATIENT);
        int doctorId = ApiSpec.userIdFor(ApiSpec.Role.DOCTOR);

        String body = """
                {
                  "patient_id": %d,
                  "doctor_id": %d,
                  "appointment_date": "2026-09-16T10:00:00",
                  "reason": "patient-created appointment -- no role gate"
                }
                """.formatted(patientId, doctorId);

        int appointmentId =
                given()
                    .spec(ApiSpec.authenticatedAs(ApiSpec.Role.PATIENT))
                    .body(body)
                .when()
                    .post("/api/appointments")
                .then()
                    .statusCode(201)
                .extract()
                    .path("appointmentId");

        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
        .when()
            .delete("/api/appointments/" + appointmentId);
    }
}
