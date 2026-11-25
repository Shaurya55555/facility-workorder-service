package com.shaurya.workorders.workorder;

import com.shaurya.workorders.asset.Asset;
import com.shaurya.workorders.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "work_orders")
public class WorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private WorkOrderStatus status = WorkOrderStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private AppUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private AppUser assignedTo;

    /** Optimistic locking: two concurrent status changes cannot both win. */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected WorkOrder() {
    }

    public WorkOrder(String title, String description, Asset asset, Priority priority, AppUser createdBy) {
        this.title = title;
        this.description = description;
        this.asset = asset;
        this.priority = priority;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void updateDetails(String title, String description, Priority priority) {
        this.title = title;
        this.description = description;
        this.priority = priority;
    }

    /** Assign (or re-assign) a technician. Only OPEN and ASSIGNED orders may be assigned. */
    public void assignTo(AppUser technician) {
        if (status == WorkOrderStatus.OPEN) {
            status.requireTransitionTo(WorkOrderStatus.ASSIGNED);
            status = WorkOrderStatus.ASSIGNED;
        } else if (status != WorkOrderStatus.ASSIGNED) {
            // IN_PROGRESS / DONE cannot be re-assigned
            throw new com.shaurya.workorders.error.InvalidTransitionException(status, WorkOrderStatus.ASSIGNED);
        }
        this.assignedTo = technician;
    }

    /** Move along the flow; throws InvalidTransitionException for any move the state machine forbids. */
    public void moveTo(WorkOrderStatus target) {
        status.requireTransitionTo(target);
        status = target;
        if (target == WorkOrderStatus.DONE) {
            completedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Asset getAsset() {
        return asset;
    }

    public Priority getPriority() {
        return priority;
    }

    public WorkOrderStatus getStatus() {
        return status;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public AppUser getAssignedTo() {
        return assignedTo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
