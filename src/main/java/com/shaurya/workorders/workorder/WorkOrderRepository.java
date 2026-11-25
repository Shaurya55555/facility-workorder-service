package com.shaurya.workorders.workorder;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long>, JpaSpecificationExecutor<WorkOrder> {

    /** Fetches asset + users in the same query so mapping a page does not trigger N+1 selects. */
    @Override
    @EntityGraph(attributePaths = {"asset", "createdBy", "assignedTo"})
    Page<WorkOrder> findAll(Specification<WorkOrder> spec, Pageable pageable);
}
