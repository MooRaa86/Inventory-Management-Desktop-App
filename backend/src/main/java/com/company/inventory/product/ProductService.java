package com.company.inventory.product;

import com.company.inventory.category.Category;
import com.company.inventory.category.CategoryRepository;
import com.company.inventory.common.error.BusinessRuleException;
import com.company.inventory.common.error.ResourceNotFoundException;
import com.company.inventory.supplier.Supplier;
import com.company.inventory.supplier.SupplierRepository;
import com.company.inventory.unit.Unit;
import com.company.inventory.unit.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UnitRepository unitRepository;
    private final SupplierRepository supplierRepository;
    private final com.company.inventory.inventory.InventoryService inventoryService;
    private final com.company.inventory.inventory.StockMovementRepository movementRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Page<ProductDto> search(ProductSearchCriteria criteria) {
        Page<Product> page = productRepository.search(
                normalize(criteria.search()),
                criteria.categoryId(), criteria.unitId(), criteria.supplierId(),
                criteria.active(), criteria.stockStatus(),
                PageRequest.of(criteria.page(), Math.min(criteria.size(), 200),
                        Sort.by(Sort.Direction.ASC, "name")));
        return page.map(ProductDto::from);
    }

    @Transactional(readOnly = true)
    public ProductDto get(Long id) {
        return ProductDto.from(find(id));
    }

    @Transactional
    public ProductDto create(ProductRequest request) {
        Product product = new Product();
        applyRequest(product, request);
        product.setCurrentStock(BigDecimal.ZERO);
        product.setActive(true);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        Product saved = productRepository.save(product);

        // Optional opening stock: recorded as a proper STOCK_IN movement so the
        // ledger and audit trail stay complete.
        if (request.openingQuantity() != null
                && request.openingQuantity().signum() > 0) {
            inventoryService.stockIn(new com.company.inventory.inventory.InventoryRequests.StockInRequest(
                    saved.getId(), request.openingQuantity(), "OPENING", "Opening stock"));
            saved = productRepository.findById(saved.getId()).orElse(saved);
        }
        return ProductDto.from(saved);
    }

    @Transactional
    public ProductDto update(Long id, ProductRequest request) {
        Product product = find(id);
        if (!product.isActive()) {
            throw new BusinessRuleException("PRODUCT_INACTIVE",
                    "Inactive products cannot be edited. Reactivate the product first.");
        }
        BigDecimal previousMin = product.getMinStock();
        applyRequest(product, request);
        if (request.maxStock() != null
                && request.maxStock().compareTo(request.minStock()) < 0) {
            throw new BusinessRuleException("MAX_BELOW_MIN",
                    "Maximum stock must be greater than or equal to minimum stock.");
        }
        product.setUpdatedAt(LocalDateTime.now());
        return ProductDto.from(productRepository.save(product));
    }

    /** Deactivation (preferred over physical delete for products with history). */
    @Transactional
    public ProductDto setActive(Long id, boolean active) {
        Product product = find(id);
        product.setActive(active);
        product.setUpdatedAt(LocalDateTime.now());
        return ProductDto.from(productRepository.save(product));
    }

    /** Record describing why a product cannot be permanently deleted. */
    public record DeleteBlocker(long movements, long purchaseItems, long issueItems) {
        public boolean hasAny() { return movements > 0 || purchaseItems > 0 || issueItems > 0; }
        public String explain() {
            var parts = new java.util.ArrayList<String>();
            if (movements > 0)      parts.add(movements + " stock movement(s)");
            if (purchaseItems > 0)  parts.add(purchaseItems + " purchase item(s)");
            if (issueItems > 0)     parts.add(issueItems + " issue item(s)");
            return "This product is referenced by " + String.join(", ", parts)
                    + ". Deactivate it instead, or remove the dependent records first.";
        }
    }

    @Transactional(readOnly = true)
    public DeleteBlocker checkDeleteBlockers(Long id) {
        find(id); // ensure exists
        long movements = movementRepository.countByProductId(id);
        long purchaseItems = jdbc.queryForObject(
                "SELECT COUNT(*) FROM purchase_items WHERE product_id = ?", Long.class, id);
        long issueItems = jdbc.queryForObject(
                "SELECT COUNT(*) FROM issue_items WHERE product_id = ?", Long.class, id);
        return new DeleteBlocker(movements, purchaseItems, issueItems);
    }

    @Transactional
    public void deletePermanently(Long id) {
        Product product = find(id);
        DeleteBlocker blocker = checkDeleteBlockers(id);
        if (blocker.hasAny()) {
            throw new BusinessRuleException("PRODUCT_HAS_HISTORY", blocker.explain());
        }
        productRepository.delete(product);
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setName(request.name().trim());
        product.setDescription(orEmpty(request.description()));
        product.setCategory(resolveCategory(request.categoryId()));
        product.setUnit(resolveUnit(request.unitId()));
        product.setSupplier(resolveSupplier(request.supplierId()));
        product.setMinStock(request.minStock());
        product.setMaxStock(request.maxStock());
        if (request.maxStock() != null && request.maxStock().compareTo(request.minStock()) < 0) {
            throw new BusinessRuleException("MAX_BELOW_MIN",
                    "Maximum stock must be greater than or equal to minimum stock.");
        }
        product.setCostCents(request.costCents());
        product.setSellCents(request.sellCents());
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessRuleException("CATEGORY_NOT_FOUND",
                        "Selected category does not exist."));
        if (!category.isActive()) {
            throw new BusinessRuleException("CATEGORY_INACTIVE", "Selected category is inactive.");
        }
        return category;
    }

    private Unit resolveUnit(Long unitId) {
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new BusinessRuleException("UNIT_NOT_FOUND",
                        "Selected unit does not exist."));
        if (!unit.isActive()) {
            throw new BusinessRuleException("UNIT_INACTIVE", "Selected unit is inactive.");
        }
        return unit;
    }

    private Supplier resolveSupplier(Long supplierId) {
        if (supplierId == null) {
            return null;
        }
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new BusinessRuleException("SUPPLIER_NOT_FOUND",
                        "Selected supplier does not exist."));
        if (!supplier.isActive()) {
            throw new BusinessRuleException("SUPPLIER_INACTIVE", "Selected supplier is inactive.");
        }
        return supplier;
    }

    private Product find(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private String normalize(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String orEmpty(String s) {
        return s == null ? "" : s;
    }

    public record ProductSearchCriteria(String search, Long categoryId, Long unitId,
                                        Long supplierId, Boolean active, String stockStatus,
                                        int page, int size) {

        public ProductSearchCriteria {
            if (stockStatus != null && stockStatus.isBlank()) {
                stockStatus = null;
            }
        }
    }
}
