package com.company.inventory.inventory;

import com.company.inventory.audit.AuditActions;
import com.company.inventory.audit.AuditService;
import com.company.inventory.common.error.BusinessRuleException;
import com.company.inventory.common.error.ResourceNotFoundException;
import com.company.inventory.product.Product;
import com.company.inventory.product.ProductRepository;
import com.company.inventory.security.AuthenticatedUser;
import com.company.inventory.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Inventory mutation engine.
 *
 * Concurrency model: SQLite allows a single writer at a time, but two threads
 * could still interleave read->validate->write and lose an update. All stock
 * mutations are therefore serialized through one fair JVM lock, making the
 * sequence read -> validate -> update -> movement -> audit atomic. This is safe
 * because exactly one application instance owns the database file (local
 * desktop deployment).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;
    private final AuditService auditService;
    private final SettingsService settingsService;

    /** Serializes all inventory mutations. Fair to prevent writer starvation. */
    private final ReentrantLock stockLock = new ReentrantLock(true);

    public MovementDto stockIn(InventoryRequests.StockInRequest request) {
        stockLock.lock();
        try {
            return doMutate(request.productId(), StockMovement.STOCK_IN, request.quantity(),
                    request.reference(), "", request.notes());
        } finally {
            stockLock.unlock();
        }
    }

    public MovementDto stockOut(InventoryRequests.StockOutRequest request) {
        stockLock.lock();
        try {
            return doMutate(request.productId(), StockMovement.STOCK_OUT, request.quantity(),
                    request.reference(), request.reason(), request.notes());
        } finally {
            stockLock.unlock();
        }
    }

    public MovementDto adjust(InventoryRequests.AdjustmentRequest request) {
        String type = request.direction() == InventoryRequests.AdjustmentDirection.IN
                ? StockMovement.ADJUSTMENT_IN : StockMovement.ADJUSTMENT_OUT;
        stockLock.lock();
        try {
            return doMutate(request.productId(), type, request.quantity(), "",
                    request.reason(), request.notes());
        } finally {
            stockLock.unlock();
        }
    }

    /** Command for batch mutations (e.g. receiving a whole purchase atomically). */
    public record MovementCommand(Long productId, String movementType, java.math.BigDecimal quantity,
                                  String reference, String reason, String notes) {
    }

    /**
     * Applies several movements under ONE lock acquisition. The caller is
     * responsible for the surrounding transaction; any failure rolls back every
     * movement and stock change in this batch.
     */
    @Transactional
    public java.util.List<MovementDto> applyMovements(java.util.List<MovementCommand> commands) {
        stockLock.lock();
        try {
            return commands.stream()
                    .map(c -> doMutate(c.productId(), c.movementType(), c.quantity(),
                            c.reference(), c.reason(), c.notes()))
                    .toList();
        } finally {
            stockLock.unlock();
        }
    }

    public record MovementDto(Long id, Long productId, String productName,
                              String movementType, BigDecimal quantity,
                              BigDecimal previousStock, BigDecimal newStock,
                              String reference, String reason, String notes,
                              Long userId, String username, LocalDateTime createdAt) {
    }

    private MovementDto toDto(StockMovement m) {
        String name = null;
        if (m.getProductId() != null) {
            name = productRepository.findById(m.getProductId())
                    .map(p -> p.getName()).orElse("");
        }
        return new MovementDto(m.getId(), m.getProductId(), name,
                m.getMovementType(), m.getQuantity(), m.getPreviousStock(), m.getNewStock(),
                m.getReference(), m.getReason(), m.getNotes(),
                m.getUserId(), m.getUsername(), m.getCreatedAt());
    }

    @Transactional
    protected MovementDto doMutate(Long productId, String movementType, BigDecimal quantity,
                                   String reference, String reason, String notes) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        if (!product.isActive() && !movementType.equals(StockMovement.ADJUSTMENT_IN)
                && !movementType.equals(StockMovement.ADJUSTMENT_OUT)) {
            throw new BusinessRuleException("PRODUCT_INACTIVE",
                    "Stock operations are not allowed on inactive products.");
        }

        BigDecimal previous = product.getCurrentStock();
        boolean increase = movementType.equals(StockMovement.STOCK_IN)
                || movementType.equals(StockMovement.ADJUSTMENT_IN)
                || movementType.equals(StockMovement.RETURN_IN);
        BigDecimal next = increase ? previous.add(quantity) : previous.subtract(quantity);

        if (next.signum() < 0 && !settingsService.getBool(
                SettingsService.INVENTORY_ALLOW_NEGATIVE, false)) {
            throw new BusinessRuleException("INSUFFICIENT_STOCK",
                    "Requested quantity (" + quantity.toPlainString()
                            + ") exceeds available stock (" + previous.toPlainString() + ").");
        }
        next = next.max(BigDecimal.ZERO).stripTrailingZeros();
        if (next.scale() < 0) {
            next = next.setScale(0);
        }

        AuthenticatedUser actor = currentUser();

        product.setCurrentStock(next);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);

        StockMovement movement = new StockMovement();
        movement.setProductId(product.getId());
        movement.setMovementType(movementType);
        movement.setQuantity(quantity.stripTrailingZeros());
        movement.setPreviousStock(previous);
        movement.setNewStock(next);
        movement.setReference(orEmpty(reference));
        movement.setReason(orEmpty(reason));
        movement.setNotes(orEmpty(notes));
        movement.setUserId(actor != null ? actor.id() : null);
        movement.setUsername(actor != null ? actor.username() : "system");
        movement.setCreatedAt(LocalDateTime.now());
        movementRepository.save(movement);

        Map<String, ?> meta = Map.of(
                "type", movementType,
                "qty", quantity.toPlainString(),
                "previous", previous.toPlainString(),
                "new", next.toPlainString());

        String action = switch (movementType) {
            case StockMovement.STOCK_IN -> AuditActions.STOCK_IN;
            case StockMovement.STOCK_OUT -> AuditActions.STOCK_OUT;
            default -> AuditActions.STOCK_ADJUSTMENT;
        };
        // Audit participates in the same transaction: if anything above failed we
        // would have thrown before reaching this line.
        auditService.log(action, "PRODUCT", product.getId(),
                movementType + " " + quantity.toPlainString() + " of '"
                        + product.getName() + "': "
                        + previous.toPlainString() + " -> " + next.toPlainString(),
                meta);

        log.info("Stock {} for product {}: {} -> {}",
                movementType, product.getId(), previous.toPlainString(), next.toPlainString());
        return toDto(movement);
    }

    @Transactional(readOnly = true)
    public Page<MovementDto> search(Long productId, String movementType, String username,
                                    String search, LocalDateTime from, LocalDateTime to,
                                    int page, int size) {
        Page<StockMovement> result = movementRepository.search(productId, movementType, username,
                normalize(search), from, to,
                PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "id")));
        return result.map(this::toDto);
    }

    private AuthenticatedUser currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u) {
            return u;
        }
        return null;
    }

    private String normalize(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
