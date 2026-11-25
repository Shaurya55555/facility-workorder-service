package com.shaurya.workorders.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base class for integration tests: the full Spring context, real Flyway migrations and the real security
 * filter chain, running against a real PostgreSQL.
 *
 * How the database starts: Testcontainers asks the local Docker daemon to run a throw-away postgres:16 container
 * on a random free port. We start it once per JVM (static singleton, so every test class shares it and the Spring
 * context is cached), then @DynamicPropertySource injects the container's JDBC URL/user/password into the
 * Spring Environment before the DataSource is created. The Ryuk sidecar removes the container when the JVM exits.
 *
 * Escape hatch for machines without Docker: set TEST_DB_URL (+ TEST_DB_USER / TEST_DB_PASSWORD) to point the
 * tests at an existing empty PostgreSQL instead. CI does not set it, so CI always uses Testcontainers.
 */
@SpringBootTest(properties = "app.seed.demo-users=true")
@AutoConfigureMockMvc
abstract class AbstractIntegrationTest {

    static final String DEMO_PASSWORD = "Password123!";
    static final String ADMIN_PASSWORD = "Admin#12345";

    private static PostgreSQLContainer<?> postgres;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String externalUrl = System.getenv("TEST_DB_URL");
        if (externalUrl != null && !externalUrl.isBlank()) {
            registry.add("spring.datasource.url", () -> externalUrl);
            registry.add("spring.datasource.username", () -> env("TEST_DB_USER", "workorders"));
            registry.add("spring.datasource.password", () -> env("TEST_DB_PASSWORD", "workorders"));
            return;
        }
        synchronized (AbstractIntegrationTest.class) {
            if (postgres == null) {
                postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
                postgres.start();
            }
        }
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;

    // ---------- helpers ----------

    protected String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    protected String adminToken() throws Exception {
        return login("admin", ADMIN_PASSWORD);
    }

    protected String managerToken() throws Exception {
        return login("manager1", DEMO_PASSWORD);
    }

    protected String tech1Token() throws Exception {
        return login("tech1", DEMO_PASSWORD);
    }

    protected String tech2Token() throws Exception {
        return login("tech2", DEMO_PASSWORD);
    }

    protected ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        request.contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + token);
        if (body != null) {
            request.content(json.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    protected JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected long createAsset(String token, String name) throws Exception {
        return read(send(post("/api/assets"), token,
                Map.of("name", name, "assetType", "HVAC", "location", "Roof")))
                .get("id").asLong();
    }

    protected long createOrder(String token, long assetId, String priority) throws Exception {
        return read(send(post("/api/work-orders"), token,
                Map.of("title", "Issue " + UUID.randomUUID(), "description", "details",
                        "assetId", assetId, "priority", priority)))
                .get("id").asLong();
    }

    protected long userId(String managerToken, String username) throws Exception {
        JsonNode users = read(send(get("/api/users"), managerToken, null));
        for (JsonNode u : users) {
            if (u.get("username").asText().equals(username)) {
                return u.get("id").asLong();
            }
        }
        throw new IllegalStateException("user not found: " + username);
    }

    protected void assign(String managerToken, long orderId, long technicianId) throws Exception {
        send(post("/api/work-orders/" + orderId + "/assign"), managerToken, Map.of("technicianId", technicianId));
    }
}
