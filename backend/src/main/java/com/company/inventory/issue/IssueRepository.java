package com.company.inventory.issue;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssueRepository extends JpaRepository<Issue, Long> {

    boolean existsByIssueNumber(String number);

    @Query("""
            SELECT i FROM Issue i
            WHERE (:search IS NULL
                   OR lower(i.issueNumber) LIKE lower(concat('%', :search, '%'))
                   OR lower(i.department) LIKE lower(concat('%', :search, '%'))
                   OR lower(i.requestedBy) LIKE lower(concat('%', :search, '%')))
              AND (:status IS NULL OR i.status = :status)
              AND (:department IS NULL OR lower(i.department) LIKE lower(concat('%', :department, '%')))
            """)
    Page<Issue> search(@Param("search") String search,
                       @Param("status") Issue.Status status,
                       @Param("department") String department,
                       Pageable pageable);
}
