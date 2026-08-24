package com.company.inventory.supplier;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query("""
            SELECT s FROM Supplier s
            WHERE (:search IS NULL
                   OR lower(s.name) LIKE lower(concat('%', :search, '%'))
                   OR lower(s.phone) LIKE lower(concat('%', :search, '%'))
                   OR lower(s.taxNumber) LIKE lower(concat('%', :search, '%')))
              AND (:active IS NULL OR s.active = :active)
            ORDER BY s.name ASC
            """)
    Page<Supplier> search(@Param("search") String search,
                          @Param("active") Boolean active,
                          Pageable pageable);
}
