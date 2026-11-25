package com.shaurya.workorders.workorder;

import org.springframework.data.jpa.domain.Specification;

/** Optional filters: a null argument means "do not filter on this". */
public final class WorkOrderSpecs {

    private WorkOrderSpecs() {
    }

    public static Specification<WorkOrder> filter(WorkOrderStatus status, Long assetId, Priority priority) {
        return Specification.where(hasStatus(status)).and(hasAsset(assetId)).and(hasPriority(priority));
    }

    private static Specification<WorkOrder> hasStatus(WorkOrderStatus status) {
        return (root, q, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    private static Specification<WorkOrder> hasAsset(Long assetId) {
        return (root, q, cb) -> assetId == null ? null : cb.equal(root.get("asset").get("id"), assetId);
    }

    private static Specification<WorkOrder> hasPriority(Priority priority) {
        return (root, q, cb) -> priority == null ? null : cb.equal(root.get("priority"), priority);
    }
}
