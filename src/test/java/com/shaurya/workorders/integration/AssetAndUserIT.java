package com.shaurya.workorders.integration;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssetAndUserIT extends AbstractIntegrationTest {

    @Test
    void managerCreatesReadsAndUpdatesAnAsset() throws Exception {
        String manager = managerToken();
        String name = "Lift " + UUID.randomUUID();

        long id = createAsset(manager, name);

        mvc.perform(get("/api/assets/" + id).header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name));

        send(put("/api/assets/" + id), manager, Map.of("name", "Renamed", "assetType", "LIFT", "location", "Lobby"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.assetType").value("LIFT"));
    }

    @Test
    void technicianCanReadButNotCreateAssets() throws Exception {
        String tech = tech1Token();

        mvc.perform(get("/api/assets").header("Authorization", "Bearer " + tech)).andExpect(status().isOk());
        send(post("/api/assets"), tech, Map.of("name", "X", "assetType", "PUMP", "location", "Y"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void assetValidationReturns400WithViolations() throws Exception {
        send(post("/api/assets"), managerToken(), Map.of("name", "", "assetType", "PUMP", "location", "Y"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("name"));
    }

    @Test
    void missingAssetIs404() throws Exception {
        mvc.perform(get("/api/assets/999999").header("Authorization", "Bearer " + managerToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void assetListIsPaginated() throws Exception {
        String manager = managerToken();
        for (int i = 0; i < 3; i++) {
            createAsset(manager, "Paged " + UUID.randomUUID());
        }

        mvc.perform(get("/api/assets?size=2&page=0&sort=name,asc").header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(3)));
    }

    @Test
    void onlyAdminDeletesAndAnAssetWithOrdersCannotBeDeleted() throws Exception {
        String manager = managerToken();
        String admin = adminToken();
        long usedAsset = createAsset(manager, "Used " + UUID.randomUUID());
        createOrder(manager, usedAsset, "LOW");
        long freeAsset = createAsset(manager, "Free " + UUID.randomUUID());

        send(delete("/api/assets/" + freeAsset), manager, null).andExpect(status().isForbidden());
        send(delete("/api/assets/" + usedAsset), admin, null).andExpect(status().isConflict());
        send(delete("/api/assets/" + freeAsset), admin, null).andExpect(status().isNoContent());
        mvc.perform(get("/api/assets/" + freeAsset).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    // ---------- users ----------

    @Test
    void adminCreatesUserWhoCanThenLogIn() throws Exception {
        String username = "tech-" + UUID.randomUUID().toString().substring(0, 8);

        send(post("/api/users"), adminToken(), Map.of("username", username, "password", "S3cret-pass", "role", "TECHNICIAN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("TECHNICIAN"));

        login(username, "S3cret-pass");   // would fail on bad credentials
    }

    @Test
    void duplicateUsernameIs409() throws Exception {
        send(post("/api/users"), adminToken(), Map.of("username", "tech1", "password", "S3cret-pass", "role", "TECHNICIAN"))
                .andExpect(status().isConflict());
    }

    @Test
    void managerCannotCreateUsersButCanListTechnicians() throws Exception {
        String manager = managerToken();

        send(post("/api/users"), manager, Map.of("username", "evil-user", "password", "S3cret-pass", "role", "ADMIN"))
                .andExpect(status().isForbidden());
        send(get("/api/users?role=TECHNICIAN"), manager, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("TECHNICIAN"));
    }

    @Test
    void technicianCannotListUsers() throws Exception {
        send(get("/api/users"), tech1Token(), null).andExpect(status().isForbidden());
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        send(post("/api/users"), adminToken(), Map.of("username", "newbie", "password", "short", "role", "TECHNICIAN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("password"));
    }
}
