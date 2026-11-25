package com.shaurya.workorders.workorder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class WorkOrderDtos {

    private WorkOrderDtos() {
    }

    public record CreateWorkOrderRequest(
            @NotBlank @Size(max = 160) String title,
            @Size(max = 4000) String description,
            @NotNull Long assetId,
            @NotNull Priority priority) {
    }

    public record UpdateWorkOrderRequest(
            @NotBlank @Size(max = 160) String title,
            @Size(max = 4000) String description,
            @NotNull Priority priority) {
    }

    public record AssignRequest(@NotNull Long technicianId) {
    }

    public record StatusChangeRequest(@NotNull WorkOrderStatus status) {
    }

    public record WorkOrderResponse(
            Long id, String title, String description,
            Long assetId, String assetName,
            Priority priority, WorkOrderStatus status,
            String createdBy, Long assignedToId, String assignedTo,
            Instant createdAt, Instant updatedAt, Instant completedAt) {

        public static WorkOrderResponse from(WorkOrder w) {
            var tech = w.getAssignedTo();
            return new WorkOrderResponse(
                    w.getId(), w.getTitle(), w.getDescription(),
                    w.getAsset().getId(), w.getAsset().getName(),
                    w.getPriority(), w.getStatus(),
                    w.getCreatedBy().getUsername(),
                    tech == null ? null : tech.getId(),
                    tech == null ? null : tech.getUsername(),
                    w.getCreatedAt(), w.getUpdatedAt(), w.getCompletedAt());
        }
    }
}
