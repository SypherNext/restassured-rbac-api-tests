package com.example.rbactests.support;

import io.restassured.path.json.JsonPath;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;

/**
 * Pulls the real OpenAPI spec out of the running backend instead of
 * hand-copying it. The spec isn't exposed as a plain JSON file by this
 * app's swagger-ui-express setup -- it's embedded inside a dynamically
 * generated swagger-ui-init.js script, so this extracts it with a regex
 * and parses the result with RestAssured's own JsonPath.
 */
public final class OpenApiSpec {

    private static final Pattern OPTIONS_PATTERN =
            Pattern.compile("var options = (\\{.*?\\n\\});\\n\\s*url = options\\.swaggerUrl", Pattern.DOTALL);

    private static volatile JsonPath cached;

    private OpenApiSpec() {
    }

    public static JsonPath fetch() {
        String script =
                given()
                    .baseUri(ApiSpec.BASE_URI)
                .when()
                    .get("/api-docs/swagger-ui-init.js")
                .then()
                    .statusCode(200)
                .extract()
                    .asString();

        Matcher matcher = OPTIONS_PATTERN.matcher(script);
        if (!matcher.find()) {
            throw new IllegalStateException("Could not find embedded spec in swagger-ui-init.js");
        }

        JsonPath options = new JsonPath(matcher.group(1));
        // setRoot scopes every later get()/getMap()/getList() call on this
        // JsonPath to start inside "swaggerDoc" -- the outer object also
        // carries a few Swagger-UI-only fields (dom_id, deepLinking, ...)
        // this course has no use for.
        options.setRoot("swaggerDoc");
        return options;
    }

    /** Cached, synchronized so parallel test execution never fetches/parses twice. */
    public static synchronized JsonPath spec() {
        if (cached == null) {
            cached = fetch();
        }
        return cached;
    }
}
