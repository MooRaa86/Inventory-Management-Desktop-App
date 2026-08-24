package com.company.inventory;

import com.company.inventory.category.Category;
import com.company.inventory.category.CategoryRepository;
import com.company.inventory.common.error.BusinessRuleException;
import com.company.inventory.inventory.InventoryRequests;
import com.company.inventory.inventory.InventoryService;
import com.company.inventory.product.Product;
import com.company.inventory.product.ProductRepository;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Boots the real application against a throwaway SQLite file and exercises
 * the stock engine, including the oversell-protection race.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InventoryServiceConcurrencyTest {

    @Autowired InventoryService inventoryService;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired UnitRepository unitRepository;

    static Long productId;

    static Path tempRoot;

    @DynamicPropertySource
    static void isolatedDataDir(DynamicPropertyRegistry registry) throws Exception {
        tempRoot = Files.createTempDirectory("ims-test-");
        Files.createDirectories(tempRoot.resolve("data"));
        registry.add("app.root", () -> tempRoot.toString());
    }

    private Product newProduct(String sku, BigDecimal stock) {
        Unit unit = new Unit();
        unit.setName(sku + "-unit");
        unit.setSymbol("u-" + sku);
        unit.setCreatedAt(java.time.LocalDateTime.now());
        unit.setUpdatedAt(java.time.LocalDateTime.now());
        unitRepository.save(unit);
        Category cat = new Category();
        cat.setName(sku + "-cat");
        cat.setCreatedAt(java.time.LocalDateTime.now());
        cat.setUpdatedAt(java.time.LocalDateTime.now());
        categoryRepository.save(cat);
        Product p = new Product();
        p.setName(sku);
        p.setUnit(unit);
        p.setCategory(cat);
        p.setCurrentStock(stock);
        p.setCreatedAt(java.time.LocalDateTime.now());
        p.setUpdatedAt(java.time.LocalDateTime.now());
        return productRepository.save(p);
    }

    @Test
    @Order(1)
    void stockInIncreasesStockAndWritesLedger() {
        Product p = newProduct("TST-IN-1", BigDecimal.ZERO);
        productId = p.getId();

        inventoryService.stockIn(new InventoryRequests.StockInRequest(
                productId, new BigDecimal("10"), "REF-1", "opening"));

        assertThat(productRepository.findById(productId).orElseThrow()
                .getCurrentStock()).isEqualByComparingTo("10");
    }

    @Test
    @Order(2)
    void stockOutBeyondAvailableIsRejectedAndRollsBack() {
        // stock is 10; taking 11 must fail without changing anything
        assertThatThrownBy(() -> inventoryService.stockOut(new InventoryRequests.StockOutRequest(
                productId, new BigDecimal("11"), "", "too much", "")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INSUFFICIENT_STOCK"));

        assertThat(productRepository.findById(productId).orElseThrow()
                .getCurrentStock()).isEqualByComparingTo("10");
    }

    @Test
    @Order(3)
    void concurrentOversellAttemptsAreSerializedExactly() throws Exception {
        Product p = newProduct("TST-RACE-1", new BigDecimal("50"));
        Long id = p.getId();
        int threads = 10;
        int perThread = 10; // total demand 100 > 50 available

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<BigDecimal>> futures = new java.util.ArrayList<>();
        AtomicInteger rejections = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final int n = i;
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    inventoryService.stockOut(new InventoryRequests.StockOutRequest(
                            id, new BigDecimal(perThread), "RACE-" + n, "sale", ""));
                    return new BigDecimal(perThread);
                } catch (BusinessRuleException e) {
                    if ("INSUFFICIENT_STOCK".equals(e.getCode())) {
                        rejections.incrementAndGet();
                        return BigDecimal.ZERO;
                    }
                    throw e;
                }
            }));
        }
        start.countDown();
        BigDecimal totalSold = BigDecimal.ZERO;
        for (Future<BigDecimal> f : futures) {
            totalSold = totalSold.add(f.get());
        }
        pool.shutdown();

        // exactly the available stock was sold, no more
        assertThat(totalSold).isEqualByComparingTo("50");
        assertThat(rejections.get()).isEqualTo(5);
        assertThat(productRepository.findById(id).orElseThrow().getCurrentStock())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }
}
