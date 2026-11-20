package com.example.rbactests.support;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.specification.RequestSpecification;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.restassured.RestAssured.given;

/**
 * Shared request-spec and authentication helper for the whole suite.
 *
 * Every test authenticates through the real {@code POST /api/auth/login}
 * endpoint -- there is no shortcut and no mock for this. Tokens are cached
 * per role for the lifetime of the JVM so a full suite run doesn't log in
 * as the same account hundreds of times.
 */
public final class ApiSpec {

    public static final String BASE_URI = System.getProperty("rbac.baseUri", "http://localhost:5000");

    public enum Role {
        ADMINISTRATOR, DOCTOR, NURSE, PATIENT
    }

    private static final Map<Role, String[]> CREDENTIALS = Map.of(
            Role.ADMINISTRATOR, new String[]{"admin", "admin123"},
            Role.DOCTOR, new String[]{"doctor1", "pass@123"},
            Role.NURSE, new String[]{"nurse1", "pass@123"},
            Role.PATIENT, new String[]{"patient1", "pass@123"}
    );

    private record LoginResult(String token, int userId) {
    }

    private static final Map<Role, LoginResult> LOGIN_CACHE = new ConcurrentHashMap<>();

    private ApiSpec() {
    }

    public static RequestSpecification base() {
        return new RequestSpecBuilder()
                .setBaseUri(BASE_URI)
                .setContentType("application/json")
                .addFilter(new RequestLoggingFilter())
                .addFilter(new ResponseLoggingFilter())
                .build();
    }

    /** Username/password pair for a role, e.g. for @CsvSource-style tests. */
    public static String[] credentialsFor(Role role) {
        return CREDENTIALS.get(role);
    }

    public static String loginAndGetToken(Role role) {
        return LOGIN_CACHE.computeIfAbsent(role, ApiSpec::login).token();
    }

    /**
     * The CURRENT real database id behind a seeded account, resolved live
     * from its own login response rather than hardcoded. This app's seed
     * script does not guarantee stable ids across reseeds (confirmed: a
     * reseed during suite development moved doctor1 from id 5 to id 47) --
     * every test in this suite that needs "doctor1's id" or "patient1's
     * id" should call this instead of hardcoding a number.
     */
    public static int userIdFor(Role role) {
        return LOGIN_CACHE.computeIfAbsent(role, ApiSpec::login).userId();
    }

    private static LoginResult login(Role role) {
        String[] creds = CREDENTIALS.get(role);
        String body = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", creds[0], creds[1]);

        var response =
                given()
                    .spec(base())
                    .body(body)
                .when()
                    .post("/api/auth/login")
                .then()
                    .statusCode(200)
                .extract()
                    .response();

        return new LoginResult(response.jsonPath().getString("token"), response.jsonPath().getInt("user.id"));
    }

    public static RequestSpecification authenticatedAs(Role role) {
        String token = loginAndGetToken(role);
        return new RequestSpecBuilder()
                .addRequestSpecification(base())
                .addHeader("Authorization", "Bearer " + token)
                .build();
    }

    /**
     * Finds a real patient id, as Administrator, that is NOT the given id.
     * Used by object-level-authorization tests that need "some other
     * patient" without hardcoding which one, since the seed script's ids
     * aren't guaranteed stable across reseeds.
     */
    public static int anyPatientIdOtherThan(int excludedId) {
        java.util.List<Integer> ids =
                given()
                    .spec(authenticatedAs(Role.ADMINISTRATOR))
                .when()
                    .get("/api/patients")
                .then()
                    .statusCode(200)
                .extract()
                    .jsonPath()
                    .getList("id", Integer.class);

        return ids.stream()
                .filter(id -> id != excludedId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No other patient found besides id " + excludedId));
    }

    /**
     * Finds a real appointment id, as Administrator, whose patient_id is
     * NOT the given id -- "an appointment belonging to someone else".
     */
    public static int anyAppointmentIdForPatientOtherThan(int excludedPatientId) {
        var response =
                given()
                    .spec(authenticatedAs(Role.ADMINISTRATOR))
                .when()
                    .get("/api/appointments")
                .then()
                    .statusCode(200)
                .extract()
                    .response();

        java.util.List<Integer> appointmentIds = response.jsonPath().getList("id", Integer.class);
        java.util.List<Integer> patientIds = response.jsonPath().getList("patient_id", Integer.class);

        for (int i = 0; i < appointmentIds.size(); i++) {
            if (patientIds.get(i) != excludedPatientId) {
                return appointmentIds.get(i);
            }
        }
        throw new IllegalStateException("No appointment found for a patient other than id " + excludedPatientId);
    }
}
