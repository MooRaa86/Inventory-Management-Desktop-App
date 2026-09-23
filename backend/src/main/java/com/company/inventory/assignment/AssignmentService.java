package com.company.inventory.assignment;

import com.company.inventory.audit.AuditActions;
import com.company.inventory.audit.AuditService;
import com.company.inventory.common.error.BusinessRuleException;
import com.company.inventory.common.error.ResourceNotFoundException;
import com.company.inventory.inventory.InventoryService;
import com.company.inventory.inventory.StockMovement;
import com.company.inventory.product.Product;
import com.company.inventory.product.ProductRepository;
import com.company.inventory.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Assignment engine: tracks which quantity of a product is "with" a holder.
 *
 * Semantics:
 *  - assign   : moves quantity OUT of central stock into a holder's hands
 *  - unassign : moves quantity back INTO central stock from a holder
 *  - transfer : moves quantity between two holders; central stock unchanged
 *
 * All three are serialized through a fair JVM lock (same idea as InventoryService)
 * so read -> validate -> update sequences stay atomic under SQLite's single-writer
 * model. Stock side effects go through InventoryService.applyMovements which holds
 * its own lock, so the combination is always atomic.
 */
@RequiredArgsConstructor
@Service
public class AssignmentService {

    private final ProductAssignmentRepository assignmentRepository;
    private final AssignmentTransferRepository transferRepository;
    private final ProductRepository productRepository;
    private final HolderService holderService;
    private final InventoryService inventoryService;
    private final AuditService auditService;

    private final ReentrantLock assignmentLock = new ReentrantLock(true);

    public record AssignRequest(Long productId, Long holderId, BigDecimal quantity, String notes) {
    }

    public record UnassignRequest(Long productId, Long holderId, BigDecimal quantity, String notes) {
    }

    public record TransferRequest(Long productId, Long fromHolderId, Long toHolderId,
                                  BigDecimal quantity, String notes) {
    }

    public record AssignmentDto(Long id, Long productId, String productName,
                                Long holderId, String holderName, String holderType,
                                BigDecimal quantity, BigDecimal assignedTotal,
                                String notes, LocalDateTime assignedAt,
                                String assignedBy, LocalDateTime updatedAt) {
    }

    public record TransferDto(Long id, Long productId, String productName, String action,
                              Long fromHolderId, String fromHolderName,
                              Long toHolderId, String toHolderName,
                              BigDecimal quantity, String notes,
                              String transferBy, LocalDateTime createdAt) {
    }

    // ---------------------------------------------------------------- assign

    @Transactional
    public AssignmentDto assign(AssignRequest request) {
        Product product = requireActiveProduct(request.productId());
        Holder holder = requireActiveHolder(request.holderId());
        BigDecimal qty = requirePositive(request.quantity(), "Assigned quantity");

        assignmentLock.lock();
        try {
            inventoryService.applyMovements(List.of(new InventoryService.MovementCommand(
                    product.getId(), StockMovement.STOCK_OUT, qty,
                    holder.getName(), "Assigned to " + holder.getName(),
                    orEmpty(request.notes()))));

            ProductAssignment existing = assignmentRepository
                    .findByProductIdAndHolderId(product.getId(), holder.getId()).orElse(null);
            ProductAssignment pa = existing != null ? existing : new ProductAssignment();
            if (existing == null) {
                pa.setProduct(product);
                pa.setHolder(holder);
                pa.setAssignedAt(LocalDateTime.now());
                pa.setAssignedBy(username());
            }
            pa.setQuantity(pa.getQuantity() == null
                    ? qty : pa.getQuantity().add(qty));
            pa.setNotes(orEmpty(request.notes()));
            pa.setUpdatedAt(LocalDateTime.now());
            assignmentRepository.save(pa);

            recordTransfer(AssignmentTransfer.ASSIGN, product, null, holder, qty,
                    orEmpty(request.notes()));
            auditService.log(AuditActions.ASSIGNMENT_ASSIGN, "PRODUCT", product.getId(),
                    "Assigned " + qty.toPlainString() + " of '" + product.getName()
                            + "' to " + holder.getName(), Map.of());
            return toDto(pa);
        } finally {
            assignmentLock.unlock();
        }
    }

    // ---------------------------------------------------------------- unassign

    @Transactional
    public AssignmentDto unassign(UnassignRequest request) {
        Product product = requireActiveProduct(request.productId());
        Holder holder = holderService.find(request.holderId());
        BigDecimal qty = requirePositive(request.quantity(), "Unassigned quantity");

        assignmentLock.lock();
        try {
            ProductAssignment pa = assignmentRepository
                    .findByProductIdAndHolderId(product.getId(), holder.getId())
                    .orElseThrow(() -> new BusinessRuleException("NOT_ASSIGNED",
                            "'" + product.getName() + "' is not assigned to " + holder.getName() + "."));
            if (pa.getQuantity().compareTo(qty) < 0) {
                throw new BusinessRuleException("INSUFFICIENT_ASSIGNED",
                        "Holder " + holder.getName() + " has only "
                                + pa.getQuantity().stripTrailingZeros().toPlainString()
                                + " of '" + product.getName() + "' to return.");
            }

            inventoryService.applyMovements(List.of(new InventoryService.MovementCommand(
                    product.getId(), StockMovement.STOCK_IN, qty,
                    holder.getName(), "Unassigned from " + holder.getName(),
                    orEmpty(request.notes()))));

            BigDecimal remaining = pa.getQuantity().subtract(qty);
            if (remaining.signum() <= 0) {
                assignmentRepository.delete(pa);
            } else {
                pa.setQuantity(remaining);
                pa.setUpdatedAt(LocalDateTime.now());
                assignmentRepository.save(pa);
            }

            recordTransfer(AssignmentTransfer.UNASSIGN, product, holder, null, qty,
                    orEmpty(request.notes()));
            auditService.log(AuditActions.ASSIGNMENT_UNASSIGN, "PRODUCT", product.getId(),
                    "Unassigned " + qty.toPlainString() + " of '" + product.getName()
                            + "' from " + holder.getName(), Map.of());
            return new AssignmentDto(pa.getId(), product.getId(), product.getName(),
                    holder.getId(), holder.getName(), holder.getType(), remaining.max(BigDecimal.ZERO),
                    BigDecimal.ZERO, orEmpty(request.notes()), pa.getAssignedAt(),
                    pa.getAssignedBy(), LocalDateTime.now());
        } finally {
            assignmentLock.unlock();
        }
    }

    // ---------------------------------------------------------------- transfer

    @Transactional
    public AssignmentDto transfer(TransferRequest request) {
        Product product = requireActiveProduct(request.productId());
        Holder fromHolder = holderService.find(request.fromHolderId());
        Holder toHolder = requireActiveHolder(request.toHolderId());
        BigDecimal qty = requirePositive(request.quantity(), "Transferred quantity");
        if (fromHolder.getId().equals(toHolder.getId())) {
            throw new BusinessRuleException("SAME_HOLDER",
                    "Source and destination holders must be different.");
        }

        assignmentLock.lock();
        try {
            ProductAssignment from = assignmentRepository
                    .findByProductIdAndHolderId(product.getId(), fromHolder.getId())
                    .orElseThrow(() -> new BusinessRuleException("NOT_ASSIGNED",
                            "'" + product.getName() + "' is not assigned to " + fromHolder.getName() + "."));
            if (from.getQuantity().compareTo(qty) < 0) {
                throw new BusinessRuleException("INSUFFICIENT_ASSIGNED",
                        fromHolder.getName() + " has only "
                                + from.getQuantity().stripTrailingZeros().toPlainString()
                                + " of '" + product.getName() + "' to transfer.");
            }

            BigDecimal remaining = from.getQuantity().subtract(qty);
            if (remaining.signum() <= 0) {
                assignmentRepository.delete(from);
            } else {
                from.setQuantity(remaining);
                from.setUpdatedAt(LocalDateTime.now());
                assignmentRepository.save(from);
            }

            ProductAssignment to = assignmentRepository
                    .findByProductIdAndHolderId(product.getId(), toHolder.getId())
                    .orElseGet(() -> {
                        ProductAssignment pa = new ProductAssignment();
                        pa.setProduct(product);
                        pa.setHolder(toHolder);
                        pa.setAssignedAt(LocalDateTime.now());
                        pa.setAssignedBy(username());
                        pa.setQuantity(BigDecimal.ZERO);
                        return pa;
                    });
            to.setQuantity(to.getQuantity() == null
                    ? qty : to.getQuantity().add(qty));
            to.setNotes(orEmpty(request.notes()));
            to.setUpdatedAt(LocalDateTime.now());
            assignmentRepository.save(to);

            recordTransfer(AssignmentTransfer.TRANSFER, product, fromHolder, toHolder, qty,
                    orEmpty(request.notes()));
            auditService.log(AuditActions.ASSIGNMENT_TRANSFER, "PRODUCT", product.getId(),
                    "Transferred " + qty.toPlainString() + " of '" + product.getName()
                            + "' from " + fromHolder.getName() + " to " + toHolder.getName(), Map.of());
            return toDto(to);
        } finally {
            assignmentLock.unlock();
        }
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public Page<AssignmentDto> search(Long productId, Long holderId, String search,
                                      int page, int size) {
        var spec = assignmentSpec(productId, holderId, search);
        return assignmentRepository.findAll(spec,
                        PageRequest.of(page, Math.min(size, 200),
                                Sort.by(Sort.Direction.ASC, "product.name")))
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<AssignmentDto> listByHolder(Long holderId) {
        return assignmentRepository.findByHolderId(holderId).stream()
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AssignmentDto> listByProduct(Long productId) {
        return assignmentRepository.findByProductId(productId).stream()
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Page<TransferDto> history(Long productId, Long holderId, String search,
                                     int page, int size) {
        var spec = transferSpec(productId, holderId, search);
        return transferRepository.findAll(spec,
                        PageRequest.of(page, Math.min(size, 200),
                                Sort.by(Sort.Direction.DESC, "id")))
                .map(this::toTransferDto);
    }

    /** Sum of quantity currently assigned per product across all holders. */
    public Map<Long, BigDecimal> assignedSumsByProduct(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return assignmentRepository.findAll().stream()
                .filter(pa -> productIds.contains(pa.getProduct().getId()))
                .collect(Collectors.groupingBy(pa -> pa.getProduct().getId(),
                        Collectors.reducing(BigDecimal.ZERO, ProductAssignment::getQuantity, BigDecimal::add)));
    }

    public BigDecimal assignedSum(Long productId) {
        return assignmentRepository.sumQuantityByProductId(productId);
    }

    // ---------------------------------------------------------------- helpers

    private org.springframework.data.jpa.domain.Specification<ProductAssignment> assignmentSpec(
            Long productId, Long holderId, String search) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
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
    }

    private org.springframework.data.jpa.domain.Specification<AssignmentTransfer> transferSpec(
            Long productId, Long holderId, String search) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (productId != null) {
                predicates.add(cb.equal(root.get("product").get("id"), productId));
            }
            if (holderId != null) {
                predicates.add(cb.or(
                        cb.equal(root.get("fromHolder").get("id"), holderId),
                        cb.equal(root.get("toHolder").get("id"), holderId)));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("product").get("name")), pattern));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private void recordTransfer(String action, Product product, Holder from,
                                Holder to, BigDecimal qty, String notes) {
        AssignmentTransfer t = new AssignmentTransfer();
        t.setProduct(product);
        t.setFromHolder(from);
        t.setToHolder(to);
        t.setQuantity(qty);
        t.setAction(action);
        t.setNotes(notes);
        t.setTransferBy(username());
        t.setCreatedAt(LocalDateTime.now());
        transferRepository.save(t);
    }

    private AssignmentDto toDto(ProductAssignment pa) {
        BigDecimal assignedTotal = BigDecimal.ZERO;
        if (pa.getProduct() != null && pa.getProduct().getId() != null) {
            try {
                assignedTotal = assignmentRepository.sumQuantityByProductId(pa.getProduct().getId());
            } catch (Exception ignored) {
            }
        }
        return new AssignmentDto(pa.getId(),
                pa.getProduct().getId(), pa.getProduct().getName(),
                pa.getHolder().getId(), pa.getHolder().getName(), pa.getHolder().getType(),
                pa.getQuantity(), assignedTotal, pa.getNotes(),
                pa.getAssignedAt(), pa.getAssignedBy(), pa.getUpdatedAt());
    }

    private TransferDto toTransferDto(AssignmentTransfer t) {
        String fromName = t.getFromHolder() != null ? t.getFromHolder().getName() : "";
        String toName = t.getToHolder() != null ? t.getToHolder().getName() : "";
        return new TransferDto(t.getId(), t.getProduct().getId(), t.getProduct().getName(),
                t.getAction(),
                t.getFromHolder() != null ? t.getFromHolder().getId() : null, fromName,
                t.getToHolder() != null ? t.getToHolder().getId() : null, toName,
                t.getQuantity(), t.getNotes(), t.getTransferBy(), t.getCreatedAt());
    }

    private Product requireActiveProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        if (!product.isActive()) {
            throw new BusinessRuleException("PRODUCT_INACTIVE", "Product is inactive.");
        }
        return product;
    }

    private Holder requireActiveHolder(Long holderId) {
        Holder holder = holderService.find(holderId);
        if (!holder.isActive()) {
            throw new BusinessRuleException("HOLDER_INACTIVE", "Holder is inactive.");
        }
        return holder;
    }

    private BigDecimal requirePositive(BigDecimal qty, String label) {
        if (qty == null || qty.signum() <= 0) {
            throw new BusinessRuleException("INVALID_QUANTITY",
                    label + " must be greater than zero.");
        }
        if (qty.stripTrailingZeros().scale() > 3) {
            throw new BusinessRuleException("INVALID_QUANTITY",
                    label + " supports at most 3 decimal places.");
        }
        return qty.stripTrailingZeros();
    }

    private String username() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u) {
            return u.username();
        }
        return "system";
    }

    private String orEmpty(String s) {
        return s == null ? "" : s;
    }
}