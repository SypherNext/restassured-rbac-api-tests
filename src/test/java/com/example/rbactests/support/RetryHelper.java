package com.example.rbactests.support;

import io.restassured.response.Response;

import java.util.function.Supplier;

/**
 * Small piece of test infrastructure: retries a request a few times if it
 * comes back with a server error, instead of failing on the first 5xx.
 * This is OUR code, not the app under test -- which is exactly why the
 * mocking module tests it with WireMock instead of the real backend.
 */
public final class RetryHelper {

    private RetryHelper() {
    }

    public static Response withRetries(Supplier<Response> request, int maxAttempts) {
        Response lastResponse = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            lastResponse = request.get();
            if (lastResponse.getStatusCode() < 500) {
                return lastResponse;
            }
        }
        return lastResponse;
    }
}
