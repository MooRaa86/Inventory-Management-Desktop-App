package com.company.inventory;

import com.company.inventory.category.Category;
import com.company.inventory.category.CategoryRepository;
import com.company.inventory.common.error.BusinessRuleException;
import com.company.inventory.product.Product;
import com.company.inventory.product.ProductRepository;
import com.company.inventory.purchase.PurchaseService;
import com.company.inventory.supplier.Supplier;
import com.company.inventory.supplier.SupplierRepository;
import com.company.inventory.unit.Unit;
import com.company.inventory.unit.UnitRepository;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PurchaseLifecycleTest {

    @Autowired PurchaseService purchaseService;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired UnitRepository unitRepository;
    @Autowired SupplierRepository supplierRepository;

    static Long supplierId;
    static Long productId;
    static Long purchaseId;

    static Path tempRoot;

    @DynamicPropertySource
    static void isolatedDataDir(DynamicPropertyRegistry registry) throws Exception {
        tempRoot = Files.createTempDirectory("ims-test-");
        Files.createDirectories(tempRoot.resolve("data"));
        registry.add("app.root", () -> tempRoot.toString());
    }


    @Test
    @Order(1)
    void draftReceiveAndStockEffects() {
        Unit unit = new Unit(); unit.setName("P-UNIT"); unit.setSymbol("pc");
        unit.setCreatedAt(java.time.LocalDateTime.now()); unit.setUpdatedAt(java.time.LocalDateTime.now());
        unitRepository.save(unit);
        Category cat = new Category(); cat.setName("P-CAT");
        cat.setCreatedAt(java.time.LocalDateTime.now()); cat.setUpdatedAt(java.time.LocalDateTime.now());
        categoryRepository.save(cat);
        Product p = new Product();
        p.setName("Purchased item");
        p.setUnit(unit); p.setCategory(cat);
        p.setCurrentStock(BigDecimal.ZERO);
        p.setCreatedAt(java.time.LocalDateTime.now());
        p.setUpdatedAt(java.time.LocalDateTime.now());
        productId = productRepository.save(p).getId();

        Supplier s = new Supplier();
        s.setName("Acme Supplies");
        s.setCreatedAt(java.time.LocalDateTime.now());
        s.setUpdatedAt(java.time.LocalDateTime.now());
        supplierId = supplierRepository.save(s).getId();

        PurchaseService.PurchaseDto draft = purchaseService.create(new PurchaseService.PurchaseCreateRequest(
                supplierId, null, "test order",
                java.util.List.of(new PurchaseService.PurchaseCreateRequest.Item(
                        productId, new BigDecimal("25"), new BigDecimal("2.50")))));
        purchaseId = draft.id();

        assertThat(draft.status()).isEqualTo(com.company.inventory.purchase.Purchase.Status.PENDING);
        assertThat(draft.totalCents()).isEqualTo(6250L);
        // stock must NOT change while still a draft
        assertThat(productRepository.findById(productId).orElseThrow().getCurrentStock())
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThatThrownBy(() -> purchaseService.receive(purchaseId + 999))
                .isInstanceOf(com.company.inventory.common.error.ApiException.class);

        PurchaseService.PurchaseDto received = purchaseService.receive(purchaseId);
        assertThat(received.status()).isEqualTo(com.company.inventory.purchase.Purchase.Status.RECEIVED);
        assertThat(productRepository.findById(productId).orElseThrow().getCurrentStock())
                .isEqualByComparingTo("25");

        // receiving twice is rejected
        assertThatThrownBy(() -> purchaseService.receive(purchaseId))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INVALID_PURCHASE_STATE"));
    }
}
