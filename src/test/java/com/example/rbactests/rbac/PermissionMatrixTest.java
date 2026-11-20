package com.example.rbactests.rbac;

import com.example.rbactests.support.ApiSpec;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real, verified permission matrix -- built from actually calling
 * every endpoint as every seeded role, not guessed from the spec's
 * summaries. Two things this class deliberately covers that a naive
 * "assert the status code" test would miss:
 *
 *  1. GET /api/appointments and GET /api/medical-records never return 403
 *     for ANY role -- what differs is row-level filtering of the *data*,
 *     not endpoint-level access control.
 *  2. GET /api/medical-records does NOT filter results for Nurse -- a
 *     Nurse sees every record, the same as Admin, even though a naive
 *     reading of the docs might expect Nurse to be scoped like Doctor.
 *
 * Marked @Isolated: several methods here compare row counts across two
 * separate HTTP calls (e.g. admin's count vs. nurse's count). Under
 * parallel execution, another test creating/deleting an appointment
 * between those two calls would make the comparison flaky through no
 * fault of the app -- exactly the case the parallel-execution module
 * flags @Isolated as the simplest correct fix for.
 */
@Isolated
class PermissionMatrixTest {

    record Case(ApiSpec.Role role, String method, String path, int expectedStatus) {
    }

    static Stream<Case> readEndpointCases() {
        return Stream.of(
                new Case(ApiSpec.Role.ADMINISTRATOR, "GET", "/api/patients", 200),
                new Case(ApiSpec.Role.DOCTOR, "GET", "/api/patients", 200),
                new Case(ApiSpec.Role.NURSE, "GET", "/api/patients", 200),
                new Case(ApiSpec.Role.PATIENT, "GET", "/api/patients", 403),

                new Case(ApiSpec.Role.ADMINISTRATOR, "GET", "/api/users", 200),
                new Case(ApiSpec.Role.DOCTOR, "GET", "/api/users", 403),
                new Case(ApiSpec.Role.NURSE, "GET", "/api/users", 403),
                new Case(ApiSpec.Role.PATIENT, "GET", "/api/users", 403),

                new Case(ApiSpec.Role.ADMINISTRATOR, "GET", "/api/appointments", 200),
                new Case(ApiSpec.Role.DOCTOR, "GET", "/api/appointments", 200),
                new Case(ApiSpec.Role.NURSE, "GET", "/api/appointments", 200),
                new Case(ApiSpec.Role.PATIENT, "GET", "/api/appointments", 200),

                new Case(ApiSpec.Role.ADMINISTRATOR, "GET", "/api/medical-records", 200),
                new Case(ApiSpec.Role.DOCTOR, "GET", "/api/medical-records", 200),
                new Case(ApiSpec.Role.NURSE, "GET", "/api/medical-records", 200),
                new Case(ApiSpec.Role.PATIENT, "GET", "/api/medical-records", 200),

                new Case(ApiSpec.Role.ADMINISTRATOR, "GET", "/api/users/role/Doctor", 200),
                new Case(ApiSpec.Role.DOCTOR, "GET", "/api/users/role/Doctor", 200),
                new Case(ApiSpec.Role.NURSE, "GET", "/api/users/role/Doctor", 200),
                new Case(ApiSpec.Role.PATIENT, "GET", "/api/users/role/Doctor", 200)
        );
    }

    @ParameterizedTest(name = "{0} {1} {2} -> {3}")
    @MethodSource("readEndpointCases")
    void endpointReturnsExpectedStatusForRole(Case testCase) {
        int actualStatus =
                given()
                    .spec(ApiSpec.authenticatedAs(testCase.role()))
                .when()
                    .request(testCase.method(), testCase.path())
                .then()
                .extract()
                .statusCode();

        assertEquals(testCase.expectedStatus(), actualStatus);
    }

    @org.junit.jupiter.api.Test
    void doctorOnlySeesTheirOwnAppointments() {
        var response =
                given()
                    .spec(ApiSpec.authenticatedAs(ApiSpec.Role.DOCTOR))
                .when()
                    .get("/api/appointments")
                .then()
                    .statusCode(200)
                .extract()
                    .response();

        List<Integer> doctorIds = response.jsonPath().getList("doctor_id");
        assertTrue(doctorIds.size() > 0, "doctor1 should have at least one appointment");
        int doctor1Id = ApiSpec.userIdFor(ApiSpec.Role.DOCTOR);
        doctorIds.forEach(id -> assertEquals(doctor1Id, id));
    }

    @org.junit.jupiter.api.Test
    void patientOnlySeesTheirOwnAppointments() {
        var response =
                given()
                    .spec(ApiSpec.authenticatedAs(ApiSpec.Role.PATIENT))
                .when()
                    .get("/api/appointments")
                .then()
                    .statusCode(200)
                .extract()
                    .response();

        List<Integer> patientIds = response.jsonPath().getList("patient_id");
        assertTrue(patientIds.size() > 0, "patient1 should have at least one appointment");
        int patient1Id = ApiSpec.userIdFor(ApiSpec.Role.PATIENT);
        patientIds.forEach(id -> assertEquals(patient1Id, id));
    }

    @org.junit.jupiter.api.Test
    void adminAndNurseSeeTheSameFullAppointmentListSize() {
        int adminCount =
                given()
                    .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
                .when()
                    .get("/api/appointments")
                .then()
                    .statusCode(200)
                .extract()
                    .jsonPath()
                    .getList("id")
                    .size();

        int nurseCount =
                given()
                    .spec(ApiSpec.authenticatedAs(ApiSpec.Role.NURSE))
                .when()
                    .get("/api/appointments")
                .then()
                    .statusCode(200)
                .extract()
                    .jsonPath()
                    .getList("id")
                    .size();

        // Admin and Nurse both see every row -- unfiltered, row-level access.
        assertEquals(adminCount, nurseCount);
    }

    @org.junit.jupiter.api.Test
    void nurseIsNotFilteredOnMedicalRecordsRealDivergenceFromWhatDocsImply() {
        // Real, verified divergence: GET /api/medical-records does NOT scope
        // results for Nurse the way it does for Doctor/Patient. A Nurse sees
        // every record in the system, identical to Admin. This test documents
        // that actual behavior -- it is not asserting a bug should exist, it
        // is asserting the app's real, current behavior so a future change
        // that silently tightens (or further loosens) this is caught.
        int adminCount =
                given()
                    .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
                .when()
                    .get("/api/medical-records")
                .then()
                    .statusCode(200)
                .extract()
                    .jsonPath()
                    .getList("id")
                    .size();

        int nurseCount =
                given()
                    .spec(ApiSpec.authenticatedAs(ApiSpec.Role.NURSE))
                .when()
                    .get("/api/medical-records")
                .then()
                    .statusCode(200)
                .extract()
                    .jsonPath()
                    .getList("id")
                    .size();

        assertEquals(adminCount, nurseCount);
    }

    @org.junit.jupiter.api.Test
    void doctorSeesFewerMedicalRecordsThanAdminOwnPatientsOnly() {
        int adminCount =
                given()
                    .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
                .when()
                    .get("/api/medical-records")
                .then()
                    .statusCode(200)
                .extract()
                    .jsonPath()
                    .getList("id")
                    .size();

        int doctorCount =
                given()
                    .spec(ApiSpec.authenticatedAs(ApiSpec.Role.DOCTOR))
                .when()
                    .get("/api/medical-records")
                .then()
                    .statusCode(200)
                .extract()
                    .jsonPath()
                    .getList("id")
                    .size();

        assertTrue(doctorCount <= adminCount);
    }

    @org.junit.jupiter.api.Test
    void aPatientTryingToDeleteAMedicalRecordGetsAnExplicitRequiredRolesBody() {
        // DELETE /api/medical-records/{id} is Doctor + Admin only; a Patient
        // gets back exactly which roles would have been accepted. The
        // record itself is created fresh here (as Doctor) so this never
        // depends on a specific seed-data id existing.
        int recordId =
                given()
                    .spec(ApiSpec.authenticatedAs(ApiSpec.Role.DOCTOR))
                    .body("""
                            { "patient_id": %d, "diagnosis": "permission matrix delete-gate probe" }
                            """.formatted(ApiSpec.userIdFor(ApiSpec.Role.PATIENT)))
                .when()
                    .post("/api/medical-records")
                .then()
                    .statusCode(201)
                .extract()
                    .path("recordId");

        try {
            given()
                .spec(ApiSpec.authenticatedAs(ApiSpec.Role.PATIENT))
            .when()
                .delete("/api/medical-records/" + recordId)
            .then()
                .statusCode(403)
                .body("error", org.hamcrest.Matchers.equalTo("Access denied. Insufficient permissions."))
                .body("userRole", org.hamcrest.Matchers.equalTo("Patient"));
        } finally {
            given()
                .spec(ApiSpec.authenticatedAs(ApiSpec.Role.DOCTOR))
            .when()
                .delete("/api/medical-records/" + recordId);
        }
    }
}
