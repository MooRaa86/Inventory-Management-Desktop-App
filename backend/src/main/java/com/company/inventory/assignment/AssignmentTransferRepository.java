package com.company.inventory.assignment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AssignmentTransferRepository extends JpaRepository<AssignmentTransfer, Long>,
        JpaSpecificationExecutor<AssignmentTransfer> {
}