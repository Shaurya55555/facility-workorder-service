package com.shaurya.workorders.workorder;

import com.shaurya.workorders.PageResponse;
import com.shaurya.workorders.security.AuthenticatedUser;
import com.shaurya.workorders.workorder.WorkOrderDtos.AssignRequest;
import com.shaurya.workorders.workorder.WorkOrderDtos.CreateWorkOrderRequest;
import com.shaurya.workorders.workorder.WorkOrderDtos.StatusChangeRequest;
import com.shaurya.workorders.workorder.WorkOrderDtos.UpdateWorkOrderRequest;
import com.shaurya.workorders.workorder.WorkOrderDtos.WorkOrderResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-orders")
public class WorkOrderController {

    private final WorkOrderService service;

    public WorkOrderController(WorkOrderService service) {
        this.service = service;
    }

    /** Any logged-in staff member can report a problem. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkOrderResponse create(@Valid @RequestBody CreateWorkOrderRequest req,
                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.create(req, actor);
    }

    @GetMapping
    public PageResponse<WorkOrderResponse> list(
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) Long assetId,
            @RequestParam(required = false) Priority priority,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(status, assetId, priority, pageable);
    }

    @GetMapping("/{id}")
    public WorkOrderResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public WorkOrderResponse update(@PathVariable Long id, @Valid @RequestBody UpdateWorkOrderRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public WorkOrderResponse assign(@PathVariable Long id, @Valid @RequestBody AssignRequest req,
                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.assign(id, req.technicianId(), actor);
    }

    /** Technicians may only call this for orders assigned to them (enforced in the service). */
    @PostMapping("/{id}/status")
    public WorkOrderResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest req,
                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.changeStatus(id, req.status(), actor);
    }
}
