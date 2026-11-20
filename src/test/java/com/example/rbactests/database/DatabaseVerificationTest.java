package com.example.rbactests.database;

import com.example.rbactests.support.ApiSpec;
import com.example.rbactests.support.Database;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads the backend's real SQLite file directly to verify writes made
 * through the API actually persisted -- a stronger check than reading
 * back through the API alone, since it rules out a whole class of bug
 * where a write-path and a read-path quietly disagree. This class only
 * ever reads the database; every write still goes through the real API.
 *
 * Skips itself (rather than failing) if the SQLite file isn't reachable
 * at the configured path -- see README for -Drbac.dbPath.
 */
class DatabaseVerificationTest {

    @BeforeEach
    void requireDatabaseFile() {
        Assumptions.assumeTrue(Database.isAvailable(),
                "rbac.db not found at the configured path -- skipping direct DB checks. " +
                "Pass -Drbac.dbPath=<path to rbac.db> if your checkout isn't a sibling folder.");
    }

    @Test
    void creatingAnAppointmentActuallyInsertsARow() throws Exception {
        int patientId = ApiSpec.userIdFor(ApiSpec.Role.PATIENT);
        int doctorId = ApiSpec.userIdFor(ApiSpec.Role.DOCTOR);

        int appointmentId =
                given()
                    .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
                    .body("""
                            {
                              "patient_id": %d,
                              "doctor_id": %d,
                              "appointment_date": "2026-10-05T09:00:00",
                              "reason": "database verification test"
                            }
                            """.formatted(patientId, doctorId))
                .when()
                    .post("/api/appointments")
                .then()
                    .statusCode(201)
                .extract()
                    .path("appointmentId");

        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT patient_id, doctor_id, status, reason FROM appointments WHERE id = ?")) {

            stmt.setInt(1, appointmentId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next(), "Expected a row for appointment id " + appointmentId);
                assertEquals(patientId, rs.getInt("patient_id"));
                assertEquals(doctorId, rs.getInt("doctor_id"));
                assertEquals("scheduled", rs.getString("status"));
                assertEquals("database verification test", rs.getString("reason"));
            }
        } finally {
            given()
                .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
            .when()
                .delete("/api/appointments/" + appointmentId);
        }
    }

    @Test
    void deletingAnAppointmentActuallyRemovesTheRow() throws Exception {
        int appointmentId =
                given()
                    .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
                    .body("""
                            {
                              "patient_id": %d,
                              "doctor_id": %d,
                              "appointment_date": "2026-10-06T09:00:00",
                              "reason": "delete verification test"
                            }
                            """.formatted(ApiSpec.userIdFor(ApiSpec.Role.PATIENT), ApiSpec.userIdFor(ApiSpec.Role.DOCTOR)))
                .when()
                    .post("/api/appointments")
                .then()
                    .statusCode(201)
                .extract()
                    .path("appointmentId");

        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
        .when()
            .delete("/api/appointments/" + appointmentId)
        .then()
            .statusCode(200);

        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT COUNT(*) AS row_count FROM appointments WHERE id = ?")) {

            stmt.setInt(1, appointmentId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                assertEquals(0, rs.getInt("row_count"));
            }
        }
    }

    @Test
    void creatingAMedicalRecordAutomaticallySetsAVisitDate() throws Exception {
        int recordId =
                given()
                    .spec(ApiSpec.authenticatedAs(ApiSpec.Role.DOCTOR))
                    .body("""
                            { "patient_id": %d, "diagnosis": "visit_date default check" }
                            """.formatted(ApiSpec.userIdFor(ApiSpec.Role.PATIENT)))
                .when()
                    .post("/api/medical-records")
                .then()
                    .statusCode(201)
                .extract()
                    .path("recordId");

        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT visit_date FROM medical_records WHERE id = ?")) {

            stmt.setInt(1, recordId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                String visitDate = rs.getString("visit_date");
                assertTrue(visitDate != null && !visitDate.isBlank(),
                        "Expected visit_date to be set automatically even though the request never included it");
            }
        } finally {
            given()
                .spec(ApiSpec.authenticatedAs(ApiSpec.Role.DOCTOR))
            .when()
                .delete("/api/medical-records/" + recordId);
        }
    }
}
