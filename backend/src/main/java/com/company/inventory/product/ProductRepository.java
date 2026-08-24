package com.company.inventory.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    long countByCategoryId(Long categoryId);

    long countByUnitId(Long unitId);

    long countBySupplierId(Long supplierId);

    /**
     * stockStatus values: IN_STOCK | LOW_STOCK | OUT_OF_STOCK (null = all).
     * Status is derived at query time; it is never stored.
     */
    @Query("""
            SELECT p FROM Product p
            LEFT JOIN FETCH p.category c
            LEFT JOIN FETCH p.unit u
            LEFT JOIN FETCH p.supplier s
            WHERE (:search IS NULL
                   OR lower(p.name) LIKE lower(concat('%', :search, '%')))
              AND (:categoryId IS NULL OR c.id = :categoryId)
              AND (:unitId IS NULL OR u.id = :unitId)
              AND (:supplierId IS NULL OR s.id = :supplierId)
              AND (:active IS NULL OR p.active = :active)
              AND (:stockStatus IS NULL OR
                   CASE WHEN p.currentStock = 0 THEN 'OUT_OF_STOCK'
                        WHEN p.currentStock <= p.minStock THEN 'LOW_STOCK'
                        ELSE 'IN_STOCK' END = :stockStatus)
            """)
    Page<Product> search(@Param("search") String search,
                         @Param("categoryId") Long categoryId,
                         @Param("unitId") Long unitId,
                         @Param("supplierId") Long supplierId,
                         @Param("active") Boolean active,
                         @Param("stockStatus") String stockStatus,
                         Pageable pageable);
}
