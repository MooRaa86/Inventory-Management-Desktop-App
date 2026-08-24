package com.company.inventory.category;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query("""
            SELECT c FROM Category c
            WHERE (:search IS NULL OR lower(c.name) LIKE lower(concat('%', :search, '%')))
              AND (:active IS NULL OR c.active = :active)
            ORDER BY c.name ASC
            """)
    Page<Category> search(@Param("search") String search,
                          @Param("active") Boolean active,
                          Pageable pageable);
}
