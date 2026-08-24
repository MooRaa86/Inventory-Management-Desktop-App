package com.company.inventory.purchase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    boolean existsByPurchaseNumber(String number);

    @Query("""
            SELECT p FROM Purchase p
            WHERE (:search IS NULL
                   OR lower(p.purchaseNumber) LIKE lower(concat('%', :search, '%'))
                   OR lower(p.supplier.name) LIKE lower(concat('%', :search, '%')))
              AND (:supplierId IS NULL OR p.supplier.id = :supplierId)
              AND (:status IS NULL OR p.status = :status)
              AND (:from IS NULL OR p.purchaseDate >= :from)
              AND (:to IS NULL OR p.purchaseDate <= :to)
            """)
    Page<Purchase> search(@Param("search") String search,
                          @Param("supplierId") Long supplierId,
                          @Param("status") Purchase.Status status,
                          @Param("from") LocalDate from,
                          @Param("to") LocalDate to,
                          Pageable pageable);
}
