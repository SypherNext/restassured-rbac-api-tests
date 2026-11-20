package com.example.rbactests.mocking;

import com.example.rbactests.support.RetryHelper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The ONE legitimate use of mocking in this suite: testing our own retry
 * helper in isolation, never the real RBAC backend. WireMock stands up a
 * fake server this test fully controls, so a specific failure sequence
 * (two 500s then a 200) can be scripted deterministically -- something
 * the real backend can't be made to reproduce reliably on demand.
 */
class RetryHelperTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    @AfterEach
    void resetStubs() {
        wireMock.resetAll();
    }

    @Test
    void retriesUntilASuccessfulResponseComesBack() {
        wireMock.stubFor(get("/flaky")
                .inScenario("retry-demo")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("second-attempt"));

        wireMock.stubFor(get("/flaky")
                .inScenario("retry-demo")
                .whenScenarioStateIs("second-attempt")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("third-attempt"));

        wireMock.stubFor(get("/flaky")
                .inScenario("retry-demo")
                .whenScenarioStateIs("third-attempt")
                .willReturn(aResponse().withStatus(200).withBody("{\"ok\":true}")));

        Response response = RetryHelper.withRetries(
                () -> given().baseUri(wireMock.baseUrl()).when().get("/flaky"),
                3
        );

        assertEquals(200, response.getStatusCode());
        wireMock.verify(3, getRequestedFor(urlEqualTo("/flaky")));
    }

    @Test
    void givesUpAfterMaxAttemptsIfEveryResponseIsAFailure() {
        wireMock.stubFor(get("/always-down").willReturn(aResponse().withStatus(500)));

        Response response = RetryHelper.withRetries(
                () -> given().baseUri(wireMock.baseUrl()).when().get("/always-down"),
                3
        );

        assertEquals(500, response.getStatusCode());
        wireMock.verify(3, getRequestedFor(urlEqualTo("/always-down")));
    }
}
