package com.example.rbactests.parameterized;

import com.example.rbactests.support.ApiSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;

/**
 * JUnit 5's four parameterized-test source types, each used where it
 * actually fits best: ValueSource for a plain list of primitives,
 * CsvSource for a small readable table, MethodSource for real objects,
 * EnumSource for enumerating ApiSpec.Role automatically (covered
 * alongside auth tests in AuthAndJwtTest).
 *
 * Marked @Isolated: appointmentVisibilityMatchesExpectation compares an
 * admin-wide count against a per-role count across two separate HTTP
 * calls, which would be flaky under parallel execution if another test
 * created or deleted an appointment in between.
 */
@Isolated
class ParameterizedExamplesTest {

    @ParameterizedTest(name = "GET /api/appointments/{0} returns 404")
    @ValueSource(ints = {0, -1, 999999})
    void invalidAppointmentIdsReturn404(int id) {
        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
        .when()
            .get("/api/appointments/" + id)
        .then()
            .statusCode(404);
    }

    @ParameterizedTest(name = "role path parameter {0} is a valid role")
    @CsvSource({"Administrator", "Doctor", "Nurse", "Patient"})
    void usersByRoleEndpointAcceptsEveryRealRole(String role) {
        given()
            .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
        .when()
            .get("/api/users/role/" + role)
        .then()
            .statusCode(200);
    }

    record VisibilityCase(ApiSpec.Role role, boolean shouldSeeEverything) {
    }

    static Stream<VisibilityCase> appointmentVisibilityCases() {
        return Stream.of(
                new VisibilityCase(ApiSpec.Role.ADMINISTRATOR, true),
                new VisibilityCase(ApiSpec.Role.NURSE, true),
                new VisibilityCase(ApiSpec.Role.DOCTOR, false),
                new VisibilityCase(ApiSpec.Role.PATIENT, false)
        );
    }

    @ParameterizedTest(name = "{0} sees everything: {1}")
    @MethodSource("appointmentVisibilityCases")
    void appointmentVisibilityMatchesExpectation(VisibilityCase testCase) {
        int adminTotal =
                given()
                    .spec(ApiSpec.authenticatedAs(ApiSpec.Role.ADMINISTRATOR))
                .when()
                    .get("/api/appointments")
                .then()
                    .statusCode(200)
                .extract()
                    .jsonPath()
                    .getList("id", Integer.class)
                    .size();

        List<Integer> roleIds =
                given()
                    .spec(ApiSpec.authenticatedAs(testCase.role()))
                .when()
                    .get("/api/appointments")
                .then()
                    .statusCode(200)
                .extract()
                    .jsonPath()
                    .getList("id", Integer.class);

        if (testCase.shouldSeeEverything()) {
            Assertions.assertEquals(adminTotal, roleIds.size());
        } else {
            Assertions.assertTrue(roleIds.size() <= adminTotal);
        }
    }
}
