package com.shaurya.workorders.error;

import com.shaurya.workorders.workorder.WorkOrderStatus;

public class InvalidTransitionException extends ConflictException {
    public InvalidTransitionException(WorkOrderStatus from, WorkOrderStatus to) {
        super("Invalid status transition: " + from + " -> " + to);
    }
}
