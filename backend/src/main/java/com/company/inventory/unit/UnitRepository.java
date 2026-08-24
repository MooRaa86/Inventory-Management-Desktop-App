package com.company.inventory.unit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnitRepository extends JpaRepository<Unit, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsBySymbolIgnoreCase(String symbol);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsBySymbolIgnoreCaseAndIdNot(String symbol, Long id);

    @Query("""
            SELECT u FROM Unit u
            WHERE (:search IS NULL OR lower(u.name) LIKE lower(concat('%', :search, '%'))
                   OR lower(u.symbol) LIKE lower(concat('%', :search, '%')))
              AND (:active IS NULL OR u.active = :active)
            ORDER BY u.name ASC
            """)
    Page<Unit> search(@Param("search") String search,
                      @Param("active") Boolean active,
                      Pageable pageable);
}
