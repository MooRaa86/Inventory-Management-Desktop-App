package com.company.inventory.purchase;

import com.company.inventory.audit.AuditActions;
import com.company.inventory.audit.AuditService;
import com.company.inventory.common.error.BusinessRuleException;
import com.company.inventory.common.error.ResourceNotFoundException;
import com.company.inventory.inventory.InventoryService;
import com.company.inventory.inventory.StockMovement;
import com.company.inventory.product.ProductRepository;
import com.company.inventory.security.AuthenticatedUser;
import com.company.inventory.supplier.SupplierRepository;
import com.company.inventory.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final AuditService auditService;
    private final UserRepository userRepository;

    public record PurchaseItemDto(Long id, Long productId, String productName,
                                  BigDecimal quantity, long unitCostCents, BigDecimal unitCostPrice,
                                  long lineTotalCents, BigDecimal lineTotal) {
    }

    public record PurchaseDto(Long id, String purchaseNumber, Long supplierId, String supplierName,
                              LocalDate purchaseDate, Purchase.Status status, long totalCents,
                              BigDecimal totalAmount, String notes, String createdByName,
                              String receivedByName, LocalDateTime receivedAt,
                              LocalDateTime createdAt, List<PurchaseItemDto> items) {
    }

    public record PurchaseCreateRequest(
            @jakarta.validation.constraints.NotNull(message = "Supplier is required")
            Long supplierId,
            LocalDate purchaseDate,
            @jakarta.validation.constraints.Size(max = 1000)
            String notes,
            @jakarta.validation.constraints.NotEmpty(message = "At least one item is required")
            @jakarta.validation.Valid
            List<Item> items) {

        public record Item(
                @jakarta.validation.constraints.NotNull Long productId,
                @jakarta.validation.constraints.NotNull BigDecimal quantity,
                @jakarta.validation.constraints.NotNull BigDecimal unitCostPrice) {
        }
    }

    @Transactional(readOnly = true)
    public Page<PurchaseDto> search(String search, Long supplierId, Purchase.Status status,
                                    LocalDate from, LocalDate to, int page, int size) {
        Page<Purchase> result = purchaseRepository.search(normalize(search), supplierId, status,
                from, to, PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "id")));
        return result.map(p -> toDto(p, false));
    }

    @Transactional(readOnly = true)
    public PurchaseDto get(Long id) {
        return toDto(find(id), true);
    }

    @Transactional
    public PurchaseDto create(PurchaseCreateRequest request) {
        var supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new BusinessRuleException("SUPPLIER_NOT_FOUND",
                        "Selected supplier does not exist."));
        if (!supplier.isActive()) {
            throw new BusinessRuleException("SUPPLIER_INACTIVE", "Selected supplier is inactive.");
        }

        Purchase purchase = new Purchase();
        purchase.setPurchaseNumber(generateNumber());
        purchase.setSupplier(supplier);
        purchase.setPurchaseDate(request.purchaseDate() != null ? request.purchaseDate() : LocalDate.now());
        purchase.setStatus(Purchase.Status.PENDING);
        purchase.setNotes(orEmpty(request.notes()));
        purchase.setCreatedBy(currentUserEntity());

        for (var item : request.items()) {
            if (item.quantity() == null || item.quantity().signum() <= 0) {
                throw new BusinessRuleException("INVALID_QUANTITY",
                        "Item quantities must be greater than zero.");
            }
            long unitCents = com.company.inventory.common.money.Money.toCents(
                    item.unitCostPrice(), "Unit cost");
            var product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new BusinessRuleException("PRODUCT_NOT_FOUND",
                            "Product id " + item.productId() + " does not exist."));
            purchase.addItem(product, item.quantity(), unitCents);
        }
        purchase.setCreatedAt(LocalDateTime.now());
        purchase.setUpdatedAt(LocalDateTime.now());
        Purchase saved = purchaseRepository.save(purchase);

        auditService.log(AuditActions.PURCHASE_CREATE, "PURCHASE", saved.getId(),
                "Created purchase " + saved.getPurchaseNumber()
                        + " (" + saved.getItems().size() + " lines, total "
                        + com.company.inventory.common.money.Money.fromCents(saved.getTotalCents()) + ")");
        return toDto(saved, true);
    }

    /** Receives the whole purchase: one transaction + one lock span + STOCK_IN per line. */
    @Transactional
    public PurchaseDto receive(Long id) {
        Purchase purchase = find(id);
        if (purchase.getStatus() != Purchase.Status.PENDING) {
            throw new BusinessRuleException("INVALID_PURCHASE_STATE",
                    "Only PENDING purchases can be received (current: " + purchase.getStatus() + ").");
        }
        if (purchase.getItems().isEmpty()) {
            throw new BusinessRuleException("EMPTY_PURCHASE", "Purchase has no items to receive.");
        }

        List<InventoryService.MovementCommand> commands = new ArrayList<>();
        for (PurchaseItem item : purchase.getItems()) {
            commands.add(new InventoryService.MovementCommand(
                    item.getProduct().getId(), StockMovement.STOCK_IN, item.getQuantity(),
                    purchase.getPurchaseNumber(), "Purchase receipt", orEmpty(purchase.getNotes())));
        }
        inventoryService.applyMovements(commands);

        purchase.setStatus(Purchase.Status.RECEIVED);
        purchase.setReceivedBy(currentUserEntity());
        purchase.setReceivedAt(LocalDateTime.now());
        purchase.setUpdatedAt(LocalDateTime.now());
        purchaseRepository.save(purchase);

        auditService.log(AuditActions.PURCHASE_RECEIVE, "PURCHASE", purchase.getId(),
                "Received purchase " + purchase.getPurchaseNumber()
                        + " (" + purchase.getItems().size() + " lines)");
        return toDto(purchase, true);
    }

    @Transactional
    public PurchaseDto cancel(Long id) {
        Purchase purchase = find(id);
        if (purchase.getStatus() != Purchase.Status.PENDING) {
            throw new BusinessRuleException("INVALID_PURCHASE_STATE",
                    "Only PENDING purchases can be cancelled (current: " + purchase.getStatus() + ").");
        }
        purchase.setStatus(Purchase.Status.CANCELLED);
        purchase.setUpdatedAt(LocalDateTime.now());
        purchaseRepository.save(purchase);
        auditService.log(AuditActions.PURCHASE_CANCEL, "PURCHASE", purchase.getId(),
                "Cancelled purchase " + purchase.getPurchaseNumber());
        return toDto(purchase, true);
    }

    private PurchaseDto toDto(Purchase p, boolean includeItems) {
        var createdName = p.getCreatedBy() != null ? p.getCreatedBy().getUsername() : "";
        var receivedName = p.getReceivedBy() != null ? p.getReceivedBy().getUsername() : null;
        List<PurchaseItemDto> itemDtos = includeItems
                ? p.getItems().stream().map(i -> new PurchaseItemDto(
                i.getId(), i.getProduct().getId(),
                i.getProduct().getName(), i.getQuantity(),
                i.getUnitCostCents(),
                com.company.inventory.common.money.Money.fromCents(i.getUnitCostCents()),
                i.getLineTotalCents(),
                com.company.inventory.common.money.Money.fromCents(i.getLineTotalCents()))).toList()
                : List.of();
        return new PurchaseDto(p.getId(), p.getPurchaseNumber(), p.getSupplier().getId(),
                p.getSupplier().getName(), p.getPurchaseDate(), p.getStatus(), p.getTotalCents(),
                com.company.inventory.common.money.Money.fromCents(p.getTotalCents()),
                p.getNotes(), createdName, receivedName, p.getReceivedAt(),
                p.getCreatedAt(), itemDtos);
    }

    private String generateNumber() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        SecureRandom random = new SecureRandom();
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "PUR-" + datePart + "-" + String.format("%03d", random.nextInt(1000));
            if (!purchaseRepository.existsByPurchaseNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique purchase number");
    }

    private com.company.inventory.user.User currentUserEntity() {
        AuthenticatedUser actor = currentUser();
        return actor == null ? null
                : userRepository.findById(actor.id()).orElse(null);
    }

    private Purchase find(Long id) {
        return purchaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found: " + id));
    }

    private AuthenticatedUser currentUser() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
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
