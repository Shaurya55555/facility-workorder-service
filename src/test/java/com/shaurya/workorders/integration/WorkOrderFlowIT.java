package com.shaurya.workorders.integration;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkOrderFlowIT extends AbstractIntegrationTest {

    // ---------- the happy path ----------

    @Test
    void fullLifecycle_open_assigned_inProgress_done() throws Exception {
        String manager = managerToken();
        String tech1 = tech1Token();
        long asset = createAsset(manager, "HVAC " + UUID.randomUUID());

        long order = createOrder(tech1, asset, "HIGH");   // any staff member can report a fault
        send(get("/api/work-orders/" + order), tech1, null)
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.assignedTo").value(nullValue()));

        send(post("/api/work-orders/" + order + "/assign"), manager, Map.of("technicianId", userId(manager, "tech1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedTo").value("tech1"));

        send(post("/api/work-orders/" + order + "/status"), tech1, Map.of("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        send(post("/api/work-orders/" + order + "/status"), tech1, Map.of("status", "DONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.completedAt").value(notNullValue()));
    }

    // ---------- role rules ----------

    @Test
    void technicianCannotAssign() throws Exception {
        String manager = managerToken();
        String tech1 = tech1Token();
        long order = createOrder(manager, createAsset(manager, "Pump " + UUID.randomUUID()), "LOW");

        send(post("/api/work-orders/" + order + "/assign"), tech1, Map.of("technicianId", userId(manager, "tech1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void technicianCannotUpdateAnotherTechniciansOrder() throws Exception {
        String manager = managerToken();
        long order = createOrder(manager, createAsset(manager, "Lift " + UUID.randomUUID()), "MEDIUM");
        assign(manager, order, userId(manager, "tech1"));

        send(post("/api/work-orders/" + order + "/status"), tech2Token(), Map.of("status", "IN_PROGRESS"))
                .andExpect(status().isForbidden());
        // and it really did not change
        send(get("/api/work-orders/" + order), manager, null).andExpect(jsonPath("$.status").value("ASSIGNED"));
    }

    @Test
    void onlyManagerOrAdminEditsAndOnlyAdminDeletes() throws Exception {
        String manager = managerToken();
        long order = createOrder(manager, createAsset(manager, "AHU " + UUID.randomUUID()), "LOW");
        Map<String, Object> edit = Map.of("title", "Edited", "description", "d", "priority", "CRITICAL");

        send(put("/api/work-orders/" + order), tech1Token(), edit).andExpect(status().isForbidden());
        send(put("/api/work-orders/" + order), manager, edit)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("CRITICAL"));
        send(delete("/api/work-orders/" + order), manager, null).andExpect(status().isForbidden());
        send(delete("/api/work-orders/" + order), adminToken(), null).andExpect(status().isNoContent());
        send(get("/api/work-orders/" + order), manager, null).andExpect(status().isNotFound());
    }

    // ---------- the state machine over HTTP ----------

    @Test
    void invalidTransitionsAreRejectedWith409() throws Exception {
        String manager = managerToken();
        String tech1 = tech1Token();
        long order = createOrder(manager, createAsset(manager, "Boiler " + UUID.randomUUID()), "HIGH");
        assign(manager, order, userId(manager, "tech1"));
        String statusUrl = "/api/work-orders/" + order + "/status";

        // skip a step: ASSIGNED -> DONE
        send(post(statusUrl), tech1, Map.of("status", "DONE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Invalid status transition: ASSIGNED -> DONE"));

        send(post(statusUrl), tech1, Map.of("status", "IN_PROGRESS")).andExpect(status().isOk());
        // go backwards: IN_PROGRESS -> OPEN
        send(post(statusUrl), tech1, Map.of("status", "OPEN")).andExpect(status().isConflict());

        send(post(statusUrl), tech1, Map.of("status", "DONE")).andExpect(status().isOk());
        // DONE is terminal
        send(post(statusUrl), tech1, Map.of("status", "IN_PROGRESS")).andExpect(status().isConflict());
        // and cannot be edited or re-assigned either
        send(put("/api/work-orders/" + order), manager, Map.of("title", "x", "priority", "LOW"))
                .andExpect(status().isConflict());
        send(post("/api/work-orders/" + order + "/assign"), manager, Map.of("technicianId", userId(manager, "tech2")))
                .andExpect(status().isConflict());
    }

    @Test
    void cannotAssignToANonTechnicianOrUnknownUser() throws Exception {
        String manager = managerToken();
        long order = createOrder(manager, createAsset(manager, "Fan " + UUID.randomUUID()), "LOW");

        send(post("/api/work-orders/" + order + "/assign"), manager, Map.of("technicianId", userId(manager, "manager1")))
                .andExpect(status().isConflict());
        send(post("/api/work-orders/" + order + "/assign"), manager, Map.of("technicianId", 987654321L))
                .andExpect(status().isNotFound());
    }

    @Test
    void reassigningAnAssignedOrderKeepsItAssigned() throws Exception {
        String manager = managerToken();
        long order = createOrder(manager, createAsset(manager, "Chiller " + UUID.randomUUID()), "LOW");
        assign(manager, order, userId(manager, "tech1"));

        send(post("/api/work-orders/" + order + "/assign"), manager, Map.of("technicianId", userId(manager, "tech2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedTo").value("tech2"));
    }

    // ---------- validation ----------

    @Test
    void createValidation() throws Exception {
        String manager = managerToken();

        send(post("/api/work-orders"), manager, Map.of("title", "", "assetId", 1, "priority", "LOW"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("title"));
        send(post("/api/work-orders"), manager, Map.of("title", "t", "assetId", 999999, "priority", "LOW"))
                .andExpect(status().isNotFound());
        send(post("/api/work-orders"), manager, Map.of("title", "t", "assetId", 1, "priority", "URGENT"))
                .andExpect(status().isBadRequest());
    }

    // ---------- pagination + filtering ----------

    @Test
    void filtersByStatusAssetAndPriority() throws Exception {
        String manager = managerToken();
        long assetA = createAsset(manager, "FilterA " + UUID.randomUUID());
        long assetB = createAsset(manager, "FilterB " + UUID.randomUUID());
        long a1 = createOrder(manager, assetA, "HIGH");
        long a2 = createOrder(manager, assetA, "LOW");
        createOrder(manager, assetB, "HIGH");
        assign(manager, a1, userId(manager, "tech1"));

        // by asset only
        send(get("/api/work-orders?assetId=" + assetA), manager, null)
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].assetId", everyItem(is((int) assetA))));
        // asset + status
        send(get("/api/work-orders?assetId=" + assetA + "&status=ASSIGNED"), manager, null)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(a1));
        // asset + priority
        send(get("/api/work-orders?assetId=" + assetA + "&priority=LOW"), manager, null)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(a2));
        // all three combined, no match
        send(get("/api/work-orders?assetId=" + assetB + "&priority=HIGH&status=DONE"), manager, null)
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void paginatesAndSorts() throws Exception {
        String manager = managerToken();
        long asset = createAsset(manager, "Paging " + UUID.randomUUID());
        for (int i = 0; i < 5; i++) {
            createOrder(manager, asset, i % 2 == 0 ? "LOW" : "HIGH");
        }

        send(get("/api/work-orders?assetId=" + asset + "&size=2&page=0"), manager, null)
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
        send(get("/api/work-orders?assetId=" + asset + "&size=2&page=2"), manager, null)
                .andExpect(jsonPath("$.content", hasSize(1)));
        send(get("/api/work-orders?assetId=" + asset + "&sort=priority,asc&size=10"), manager, null)
                .andExpect(jsonPath("$.content[0].priority").value("HIGH"));   // alphabetical: HIGH < LOW
    }

    @Test
    void badFilterOrSortValuesGive400NotAServerError() throws Exception {
        String manager = managerToken();

        send(get("/api/work-orders?status=BOGUS"), manager, null).andExpect(status().isBadRequest());
        send(get("/api/work-orders?assetId=abc"), manager, null).andExpect(status().isBadRequest());
        send(get("/api/work-orders?sort=doesNotExist,asc"), manager, null).andExpect(status().isBadRequest());
    }
}
