package org.syu_likelion.Festa_2026.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.tomcat.autoconfigure.TomcatServerProperties;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/** Uses a real HTTP connection and Tomcat parser; MockMvc does not enforce connector limits. */
class TomcatMultipartCapacityTests {
    @Test
    void smallPollFormOverFiftyPartsReproducesOld413AndWorksWithConfiguredLimits() throws Exception {
        String body = body(61, false, 0);
        assertThat(post(new TomcatServerProperties(), body).statusCode()).isEqualTo(413);
        var response = post(configured(), body);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("61:61");
    }

    @Test
    void largestPollFieldCountAndLongFilenameAreAccepted() throws Exception {
        var response = post(configured(), body(11000, true, 0));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("11001:11000");
    }

    @Test
    void textFieldsBeyondOldTwoMegabyteLimitAreAccepted() throws Exception {
        var response = post(configured(), body(1, false, 3 * 1024 * 1024));
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private TomcatServerProperties configured() throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/application.properties"))) {
            properties.load(reader);
        }
        Map<String, Object> values = new HashMap<>();
        properties.forEach((key, value) -> values.put(key.toString(), value));
        var environment = new StandardEnvironment();
        environment.getPropertySources().addLast(new MapPropertySource("application", values));
        return Binder.get(environment).bind("server.tomcat", TomcatServerProperties.class).get();
    }

    private HttpResponse<String> post(TomcatServerProperties properties, String body) throws Exception {
        var factory = new TomcatServletWebServerFactory(0);
        factory.addConnectorCustomizers(connector -> {
            connector.setMaxPartCount(properties.getMaxPartCount());
            connector.setMaxParameterCount(properties.getMaxParameterCount());
            connector.setMaxPostSize((int) properties.getMaxHttpFormPostSize().toBytes());
            connector.setMaxPartHeaderSize((int) properties.getMaxPartHeaderSize().toBytes());
        });
        var server = factory.getWebServer(context -> {
            var servlet = context.addServlet("probe", new HttpServlet() {
                @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
                    try {
                        int parts = request.getParts().size();
                        response.getWriter().print(parts + ":" + request.getParameterMap().size());
                    } catch (ServletException | IllegalStateException failure) {
                        response.sendError(413);
                    }
                }
            });
            servlet.setMultipartConfig(new MultipartConfigElement("", 200L * 1024 * 1024, 650L * 1024 * 1024, 0));
            servlet.addMapping("/probe");
        });
        try {
            server.start();
            try (var client = HttpClient.newHttpClient()) {
                return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getPort() + "/probe"))
                        .timeout(java.time.Duration.ofSeconds(30))
                        .header("Content-Type", "multipart/form-data; boundary=poll-capacity-test")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                        HttpResponse.BodyHandlers.ofString());
            }
        } finally {
            server.stop();
            server.destroy();
        }
    }

    private String body(int count, boolean file, int largeTextLength) {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < count; i++) {
            body.append("--poll-capacity-test\r\nContent-Disposition: form-data; name=\"field-")
                    .append(i).append("\"\r\n\r\n").append(i == 0 && largeTextLength > 0 ? "a".repeat(largeTextLength) : "value")
                    .append("\r\n");
        }
        if (file) body.append("--poll-capacity-test\r\nContent-Disposition: form-data; name=\"image\"; filename=\"")
                .append("가".repeat(251)).append(".jpg\"\r\nContent-Type: image/jpeg\r\n\r\nimage\r\n");
        return body.append("--poll-capacity-test--\r\n").toString();
    }
}
