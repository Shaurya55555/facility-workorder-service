package com.shaurya.workorders.workorder;

import com.shaurya.workorders.error.InvalidTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.shaurya.workorders.workorder.WorkOrderStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkOrderStatusTest {

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({"OPEN,ASSIGNED", "ASSIGNED,IN_PROGRESS", "IN_PROGRESS,DONE"})
    void forwardStepsAreAllowed(WorkOrderStatus from, WorkOrderStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
        from.requireTransitionTo(to);   // must not throw
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({
            "OPEN,IN_PROGRESS", "OPEN,DONE", "OPEN,OPEN",
            "ASSIGNED,OPEN", "ASSIGNED,DONE", "ASSIGNED,ASSIGNED",
            "IN_PROGRESS,OPEN", "IN_PROGRESS,ASSIGNED", "IN_PROGRESS,IN_PROGRESS",
            "DONE,OPEN", "DONE,ASSIGNED", "DONE,IN_PROGRESS", "DONE,DONE"})
    void everythingElseIsRejected(WorkOrderStatus from, WorkOrderStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
        assertThatThrownBy(() -> from.requireTransitionTo(to))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining(from + " -> " + to);
    }

    @Test
    void doneIsTerminal() {
        assertThat(DONE.allowedNext()).isEmpty();
    }
}
