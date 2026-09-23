package com.company.inventory.assignment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductAssignmentRepository extends JpaRepository<ProductAssignment, Long>,
        JpaSpecificationExecutor<ProductAssignment> {

    Optional<ProductAssignment> findByProductIdAndHolderId(Long productId, Long holderId);

    long countByHolderId(Long holderId);

    @Query("SELECT COALESCE(SUM(pa.quantity), 0) FROM ProductAssignment pa WHERE pa.product.id = :productId")
    BigDecimal sumQuantityByProductId(@Param("productId") Long productId);

    List<ProductAssignment> findByProductId(Long productId);

    List<ProductAssignment> findByHolderId(Long holderId);

    default Page<ProductAssignment> search(Long productId, Long holderId, String search,
                                           Pageable pageable) {
        Specification<ProductAssignment> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (productId != null) {
                predicates.add(cb.equal(root.get("product").get("id"), productId));
            }
            if (holderId != null) {
                predicates.add(cb.equal(root.get("holder").get("id"), holderId));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("product").get("name")), pattern),
                        cb.like(cb.lower(root.get("holder").get("name")), pattern)));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return findAll(spec, pageable);
    }
}