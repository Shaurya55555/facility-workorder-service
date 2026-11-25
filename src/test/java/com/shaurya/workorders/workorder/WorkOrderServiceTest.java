package com.shaurya.workorders.workorder;

import com.shaurya.workorders.asset.Asset;
import com.shaurya.workorders.asset.AssetRepository;
import com.shaurya.workorders.error.ConflictException;
import com.shaurya.workorders.error.InvalidTransitionException;
import com.shaurya.workorders.error.NotFoundException;
import com.shaurya.workorders.security.AuthenticatedUser;
import com.shaurya.workorders.user.AppUser;
import com.shaurya.workorders.user.Role;
import com.shaurya.workorders.user.UserRepository;
import com.shaurya.workorders.workorder.WorkOrderDtos.CreateWorkOrderRequest;
import com.shaurya.workorders.workorder.WorkOrderDtos.UpdateWorkOrderRequest;
import com.shaurya.workorders.workorder.WorkOrderDtos.WorkOrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    @Mock WorkOrderRepository workOrders;
    @Mock AssetRepository assets;
    @Mock UserRepository users;

    WorkOrderService service;

    AppUser manager = user(1L, "manager1", Role.MANAGER);
    AppUser tech = user(2L, "tech1", Role.TECHNICIAN);
    AppUser otherTech = user(3L, "tech2", Role.TECHNICIAN);
    AuthenticatedUser asManager = new AuthenticatedUser(1L, "manager1", Role.MANAGER);
    AuthenticatedUser asTech = new AuthenticatedUser(2L, "tech1", Role.TECHNICIAN);
    AuthenticatedUser asOtherTech = new AuthenticatedUser(3L, "tech2", Role.TECHNICIAN);
    Asset pump = asset(10L, "Pump A");

    @BeforeEach
    void setUp() {
        service = new WorkOrderService(workOrders, assets, users);
    }

    // ---------- create ----------

    @Test
    void createRejectsUnknownAsset() {
        when(assets.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateWorkOrderRequest("t", "d", 99L, Priority.HIGH), asManager))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Asset 99");
        verify(workOrders, never()).save(any());
    }

    @Test
    void createStartsOpenAndUnassigned() {
        when(assets.findById(10L)).thenReturn(Optional.of(pump));
        when(users.getReferenceById(1L)).thenReturn(manager);
        when(workOrders.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkOrderResponse res = service.create(new CreateWorkOrderRequest("Leaking", "Water", 10L, Priority.HIGH), asManager);

        assertThat(res.status()).isEqualTo(WorkOrderStatus.OPEN);
        assertThat(res.assignedTo()).isNull();
        assertThat(res.assetName()).isEqualTo("Pump A");
        assertThat(res.createdBy()).isEqualTo("manager1");
    }

    // ---------- assign ----------

    @Test
    void managerAssignsTechnician_openBecomesAssigned() {
        WorkOrder order = order(100L, WorkOrderStatus.OPEN, null);
        when(workOrders.findById(100L)).thenReturn(Optional.of(order));
        when(users.findById(2L)).thenReturn(Optional.of(tech));

        WorkOrderResponse res = service.assign(100L, 2L, asManager);

        assertThat(res.status()).isEqualTo(WorkOrderStatus.ASSIGNED);
        assertThat(res.assignedTo()).isEqualTo("tech1");
    }

    @Test
    void technicianCannotAssign() {
        assertThatThrownBy(() -> service.assign(100L, 2L, asTech))
                .isInstanceOf(AccessDeniedException.class);
        verify(workOrders, never()).findById(any());
    }

    @Test
    void cannotAssignToNonTechnician() {
        when(workOrders.findById(100L)).thenReturn(Optional.of(order(100L, WorkOrderStatus.OPEN, null)));
        when(users.findById(1L)).thenReturn(Optional.of(manager));

        assertThatThrownBy(() -> service.assign(100L, 1L, asManager))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not a technician");
    }

    @Test
    void assignedOrderCanBeReassigned() {
        WorkOrder order = order(100L, WorkOrderStatus.ASSIGNED, tech);
        when(workOrders.findById(100L)).thenReturn(Optional.of(order));
        when(users.findById(3L)).thenReturn(Optional.of(otherTech));

        WorkOrderResponse res = service.assign(100L, 3L, asManager);

        assertThat(res.status()).isEqualTo(WorkOrderStatus.ASSIGNED);
        assertThat(res.assignedTo()).isEqualTo("tech2");
    }

    @Test
    void inProgressOrderCannotBeReassigned() {
        when(workOrders.findById(100L)).thenReturn(Optional.of(order(100L, WorkOrderStatus.IN_PROGRESS, tech)));
        when(users.findById(3L)).thenReturn(Optional.of(otherTech));

        assertThatThrownBy(() -> service.assign(100L, 3L, asManager))
                .isInstanceOf(InvalidTransitionException.class);
    }

    // ---------- status changes ----------

    @Test
    void assignedTechnicianWalksTheWholeFlow() {
        WorkOrder order = order(100L, WorkOrderStatus.ASSIGNED, tech);
        when(workOrders.findById(100L)).thenReturn(Optional.of(order));

        assertThat(service.changeStatus(100L, WorkOrderStatus.IN_PROGRESS, asTech).status())
                .isEqualTo(WorkOrderStatus.IN_PROGRESS);
        WorkOrderResponse done = service.changeStatus(100L, WorkOrderStatus.DONE, asTech);

        assertThat(done.status()).isEqualTo(WorkOrderStatus.DONE);
        assertThat(done.completedAt()).isNotNull();
    }

    @Test
    void technicianCannotTouchSomeoneElsesOrder() {
        when(workOrders.findById(100L)).thenReturn(Optional.of(order(100L, WorkOrderStatus.ASSIGNED, tech)));

        assertThatThrownBy(() -> service.changeStatus(100L, WorkOrderStatus.IN_PROGRESS, asOtherTech))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("assigned to them");
    }

    @Test
    void technicianCannotTouchUnassignedOrder() {
        when(workOrders.findById(100L)).thenReturn(Optional.of(order(100L, WorkOrderStatus.OPEN, null)));

        assertThatThrownBy(() -> service.changeStatus(100L, WorkOrderStatus.IN_PROGRESS, asTech))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void skippingAStepIsRejected() {
        WorkOrder order = order(100L, WorkOrderStatus.ASSIGNED, tech);
        when(workOrders.findById(100L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.changeStatus(100L, WorkOrderStatus.DONE, asTech))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("ASSIGNED -> DONE");
        assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.ASSIGNED);   // unchanged
    }

    @Test
    void movingBackwardsIsRejected() {
        when(workOrders.findById(100L)).thenReturn(Optional.of(order(100L, WorkOrderStatus.IN_PROGRESS, tech)));

        assertThatThrownBy(() -> service.changeStatus(100L, WorkOrderStatus.OPEN, asTech))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void assignedStatusMustGoThroughAssignEndpoint() {
        when(workOrders.findById(100L)).thenReturn(Optional.of(order(100L, WorkOrderStatus.OPEN, null)));

        assertThatThrownBy(() -> service.changeStatus(100L, WorkOrderStatus.ASSIGNED, asManager))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("assign endpoint");
    }

    @Test
    void managerCanAdvanceAnAssignedOrder() {
        when(workOrders.findById(100L)).thenReturn(Optional.of(order(100L, WorkOrderStatus.ASSIGNED, tech)));

        assertThat(service.changeStatus(100L, WorkOrderStatus.IN_PROGRESS, asManager).status())
                .isEqualTo(WorkOrderStatus.IN_PROGRESS);
    }

    @Test
    void changeStatusOnMissingOrderIs404() {
        when(workOrders.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(5L, WorkOrderStatus.IN_PROGRESS, asTech))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------- update / delete ----------

    @Test
    void doneOrderCannotBeEdited() {
        when(workOrders.findById(100L)).thenReturn(Optional.of(order(100L, WorkOrderStatus.DONE, tech)));

        assertThatThrownBy(() -> service.update(100L, new UpdateWorkOrderRequest("x", "y", Priority.LOW)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateChangesDetails() {
        when(workOrders.findById(100L)).thenReturn(Optional.of(order(100L, WorkOrderStatus.OPEN, null)));

        WorkOrderResponse res = service.update(100L, new UpdateWorkOrderRequest("New title", "New desc", Priority.CRITICAL));

        assertThat(res.title()).isEqualTo("New title");
        assertThat(res.priority()).isEqualTo(Priority.CRITICAL);
    }

    // ---------- helpers ----------

    private AppUser user(Long id, String name, Role role) {
        AppUser u = new AppUser(name, "hash", role);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private Asset asset(Long id, String name) {
        Asset a = new Asset(name, "PUMP", "Basement");
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    private WorkOrder order(Long id, WorkOrderStatus status, AppUser assignedTo) {
        WorkOrder w = new WorkOrder("Broken", "desc", pump, Priority.MEDIUM, manager);
        ReflectionTestUtils.setField(w, "id", id);
        ReflectionTestUtils.setField(w, "status", status);
        ReflectionTestUtils.setField(w, "assignedTo", assignedTo);
        return w;
    }
}
