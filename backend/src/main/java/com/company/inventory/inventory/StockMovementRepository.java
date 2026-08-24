package com.company.inventory.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    long countByProductId(Long productId);

    @Query("""
            SELECT m FROM StockMovement m
            WHERE (:productId IS NULL OR m.productId = :productId)
              AND (:movementType IS NULL OR m.movementType = :movementType)
              AND (:username IS NULL OR lower(m.username) LIKE lower(concat('%', :username, '%')))
              AND (:search IS NULL
                   OR lower(m.reference) LIKE lower(concat('%', :search, '%'))
                   OR lower(m.reason) LIKE lower(concat('%', :search, '%'))
                   OR lower(m.notes) LIKE lower(concat('%', :search, '%')))
              AND (:from IS NULL OR m.createdAt >= :from)
              AND (:to IS NULL OR m.createdAt <= :to)
            """)
    Page<StockMovement> search(@Param("productId") Long productId,
                               @Param("movementType") String movementType,
                               @Param("username") String username,
                               @Param("search") String search,
                               @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to,
                               Pageable pageable);
}
