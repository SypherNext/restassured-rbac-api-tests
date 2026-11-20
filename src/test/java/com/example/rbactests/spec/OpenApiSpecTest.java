package com.example.rbactests.spec;

import com.example.rbactests.support.ApiSpec;
import com.example.rbactests.support.OpenApiSpec;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that come straight from the OpenAPI spec itself, pulled live
 * out of the running server (see OpenApiSpec) -- not from a guess, and
 * not from the Swagger UI's rendered page.
 */
class OpenApiSpecTest {

    @Test
    void specDeclaresTheRealEndpoints() {
        JsonPath spec = OpenApiSpec.spec();
        Map<String, Object> paths = spec.getMap("paths");

        assertTrue(paths.containsKey("/api/appointments"));
        assertTrue(paths.containsKey("/api/medical-records"));
        assertTrue(paths.containsKey("/api/patients"));
        assertTrue(paths.containsKey("/api/users"));
    }

    @Test
    void medicalRecordCreationRequiresPatientIdAndDiagnosis() {
        JsonPath spec = OpenApiSpec.spec();
        List<String> required = spec.getList(
                "paths.'/api/medical-records'.post.requestBody.content.'application/json'.schema.required");

        assertTrue(required.contains("patient_id"));
        assertTrue(required.contains("diagnosis"));
    }

    @Test
    void aSpecAnchoredAssertionChecksTheRealResponseAgainstDocumentedCodes() {
        JsonPath spec = OpenApiSpec.spec();
        List<Integer> documentedCodes =
                spec.getMap("paths.'/api/appointments/{id}'.get.responses")
                    .keySet().stream()
                    .map(String::valueOf)
                    .map(Integer::parseInt)
                    .toList();

        int actualCode =
                given()
                    .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
                .when()
                    .get("/api/appointments/999999")
                .then()
                .extract()
                .statusCode();

        // The spec's own documented status set is a floor, not a ceiling --
        // this app's spec only documents 200/404 here, so a real 404 for a
        // nonexistent id should fall inside that documented set.
        assertEquals(404, actualCode);
        assertTrue(documentedCodes.contains(actualCode),
                "Got status " + actualCode + " but the spec only documents " + documentedCodes + " for this operation");
    }
}
