package com.shaurya.workorders.workorder;

import com.shaurya.workorders.PageResponse;
import com.shaurya.workorders.asset.Asset;
import com.shaurya.workorders.asset.AssetRepository;
import com.shaurya.workorders.error.ConflictException;
import com.shaurya.workorders.error.NotFoundException;
import com.shaurya.workorders.security.AuthenticatedUser;
import com.shaurya.workorders.user.AppUser;
import com.shaurya.workorders.user.Role;
import com.shaurya.workorders.user.UserRepository;
import com.shaurya.workorders.workorder.WorkOrderDtos.CreateWorkOrderRequest;
import com.shaurya.workorders.workorder.WorkOrderDtos.UpdateWorkOrderRequest;
import com.shaurya.workorders.workorder.WorkOrderDtos.WorkOrderResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Business rules live here, not in the controller.
 *
 * Transactions: every method that changes data is @Transactional so the load, the checks and the write are one
 * atomic unit (a failed check rolls everything back, and Hibernate's dirty checking flushes the change on commit).
 * Read methods use readOnly = true: Hibernate skips dirty checking and the driver can route/optimise reads.
 * DTO mapping happens inside the transaction because open-in-view is off (lazy associations need a session).
 */
@Service
public class WorkOrderService {

    private final WorkOrderRepository workOrders;
    private final AssetRepository assets;
    private final UserRepository users;

    public WorkOrderService(WorkOrderRepository workOrders, AssetRepository assets, UserRepository users) {
        this.workOrders = workOrders;
        this.assets = assets;
        this.users = users;
    }

    @Transactional
    public WorkOrderResponse create(CreateWorkOrderRequest req, AuthenticatedUser actor) {
        Asset asset = assets.findById(req.assetId())
                .orElseThrow(() -> new NotFoundException("Asset " + req.assetId() + " not found"));
        AppUser creator = users.getReferenceById(actor.id());
        WorkOrder saved = workOrders.save(new WorkOrder(req.title(), req.description(), asset, req.priority(), creator));
        return WorkOrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkOrderResponse> list(WorkOrderStatus status, Long assetId, Priority priority, Pageable pageable) {
        return PageResponse.of(workOrders.findAll(WorkOrderSpecs.filter(status, assetId, priority), pageable),
                WorkOrderResponse::from);
    }

    @Transactional(readOnly = true)
    public WorkOrderResponse get(Long id) {
        return WorkOrderResponse.from(find(id));
    }

    @Transactional
    public WorkOrderResponse update(Long id, UpdateWorkOrderRequest req) {
        WorkOrder order = find(id);
        if (order.getStatus() == WorkOrderStatus.DONE) {
            throw new ConflictException("A DONE work order can no longer be edited");
        }
        order.updateDetails(req.title(), req.description(), req.priority());
        return WorkOrderResponse.from(order);
    }

    @Transactional
    public void delete(Long id) {
        workOrders.delete(find(id));
    }

    /** Manager/admin assigns a technician. OPEN -> ASSIGNED (or re-assign while still ASSIGNED). */
    @Transactional
    public WorkOrderResponse assign(Long id, Long technicianId, AuthenticatedUser actor) {
        if (actor.role() == Role.TECHNICIAN) {
            throw new AccessDeniedException("Only a manager or admin can assign work orders");
        }
        WorkOrder order = find(id);
        AppUser technician = users.findById(technicianId)
                .orElseThrow(() -> new NotFoundException("User " + technicianId + " not found"));
        if (technician.getRole() != Role.TECHNICIAN) {
            throw new ConflictException("User " + technician.getUsername() + " is not a technician");
        }
        order.assignTo(technician);
        return WorkOrderResponse.from(order);
    }

    /**
     * Move an order along ASSIGNED -> IN_PROGRESS -> DONE.
     * Order of checks matters: 404, then ownership (403), then the state machine (409).
     */
    @Transactional
    public WorkOrderResponse changeStatus(Long id, WorkOrderStatus target, AuthenticatedUser actor) {
        WorkOrder order = find(id);

        if (target == WorkOrderStatus.ASSIGNED) {
            throw new ConflictException("Use the assign endpoint to move a work order to ASSIGNED");
        }
        if (actor.role() == Role.TECHNICIAN && !isAssignedTo(order, actor)) {
            throw new AccessDeniedException("Technicians can only update work orders assigned to them");
        }

        order.moveTo(target);   // throws InvalidTransitionException (409) for illegal moves
        return WorkOrderResponse.from(order);
    }

    private static boolean isAssignedTo(WorkOrder order, AuthenticatedUser actor) {
        return order.getAssignedTo() != null && Objects.equals(order.getAssignedTo().getId(), actor.id());
    }

    private WorkOrder find(Long id) {
        return workOrders.findById(id).orElseThrow(() -> new NotFoundException("Work order " + id + " not found"));
    }
}
