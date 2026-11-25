package com.shaurya.workorders.workorder;

import com.shaurya.workorders.error.InvalidTransitionException;

import java.util.Set;

/**
 * The work-order state machine: OPEN -> ASSIGNED -> IN_PROGRESS -> DONE.
 * Each state lists the only states it may move to; DONE is terminal. Anything else is rejected.
 */
public enum WorkOrderStatus {
    OPEN, ASSIGNED, IN_PROGRESS, DONE;

    public Set<WorkOrderStatus> allowedNext() {
        return switch (this) {
            case OPEN -> Set.of(ASSIGNED);
            case ASSIGNED -> Set.of(IN_PROGRESS);
            case IN_PROGRESS -> Set.of(DONE);
            case DONE -> Set.of();
        };
    }

    public boolean canTransitionTo(WorkOrderStatus target) {
        return allowedNext().contains(target);
    }

    /** @throws InvalidTransitionException (HTTP 409) when the move is not allowed */
    public void requireTransitionTo(WorkOrderStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidTransitionException(this, target);
        }
    }
}
