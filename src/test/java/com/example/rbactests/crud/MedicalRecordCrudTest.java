package com.example.rbactests.crud;

import com.example.rbactests.support.ApiSpec;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Same create -> read -> update -> delete discipline as
 * AppointmentCrudTest, applied to /api/medical-records. Creation is
 * Doctor-only per the real RBAC rule confirmed in the permission-matrix
 * tests.
 */
class MedicalRecordCrudTest {

    @Test
    void createReadUpdateDeleteAMedicalRecord() {
        int patientId = ApiSpec.userIdFor(ApiSpec.Role.PATIENT);

        String requestBody = """
                {
                  "patient_id": %d,
                  "diagnosis": "CRUD module test diagnosis",
                  "prescription": "none",
                  "notes": "created by MedicalRecordCrudTest"
                }
                """.formatted(patientId);

        int recordId =
                given()
                    .spec(ApiSpec.authenticatedAs(ApiSpec.Role.DOCTOR))
                    .body(requestBody)
                .when()
                    .post("/api/medical-records")
                .then()
                    .statusCode(201)
                    .body("message", equalTo("Medical record created successfully"))
                    .body("recordId", notNullValue())
                .extract()
                    .path("recordId");

        try {
            given()
                .spec(ApiSpec.authenticatedAs(ApiSpec.Role.DOCTOR))
            .when()
                .get("/api/medical-records/" + recordId)
            .then()
                .statusCode(200)
                .body("id", equalTo(recordId))
                .body("patient_id", equalTo(patientId))
                .body("diagnosis", equalTo("CRUD module test diagnosis"))
                .body("patient_name", notNullValue())
                .body("doctor_name", notNullValue());

            given()
                .spec(ApiSpec.authenticatedAs(ApiSpec.Role.DOCTOR))
                .body("""
                        { "diagnosis": "updated diagnosis", "notes": "updated via test" }
                        """)
            .when()
                .put("/api/medical-records/" + recordId)
            .then()
                .statusCode(200);

            given()
                .spec(ApiSpec.authenticatedAs(ApiSpec.Role.DOCTOR))
            .when()
                .get("/api/medical-records/" + recordId)
            .then()
                .statusCode(200)
                .body("diagnosis", equalTo("updated diagnosis"))
                .body("notes", equalTo("updated via test"));

        } finally {
            given()
                .spec(ApiSpec.authenticatedAs(ApiSpec.Role.DOCTOR))
            .when()
                .delete("/api/medical-records/" + recordId)
            .then()
                .statusCode(200);

            given()
                .spec(ApiSpec.authenticatedAs(ApiSpec.Role.DOCTOR))
            .when()
                .get("/api/medical-records/" + recordId)
            .then()
                .statusCode(404);
        }
    }

    @Test
    void nurseCannotCreateAMedicalRecord() {
        int patientId = ApiSpec.userIdFor(ApiSpec.Role.PATIENT);
        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.NURSE))
            .body("""
                    { "patient_id": %d, "diagnosis": "should be rejected" }
                    """.formatted(patientId))
        .when()
            .post("/api/medical-records")
        .then()
            .statusCode(403);
    }
}
