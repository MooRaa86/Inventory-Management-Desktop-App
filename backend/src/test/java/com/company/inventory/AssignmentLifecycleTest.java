package com.company.inventory;

import com.company.inventory.assignment.AssignmentService;
import com.company.inventory.assignment.AssignmentTransfer;
import com.company.inventory.assignment.AssignmentTransferRepository;
import com.company.inventory.assignment.Holder;
import com.company.inventory.assignment.HolderRepository;
import com.company.inventory.assignment.HolderService;
import com.company.inventory.assignment.ProductAssignment;
import com.company.inventory.assignment.ProductAssignmentRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the product-assignment engine: assign moves stock OUT of central
 * inventory, unassign moves it back, transfer reassigns with no stock change,
 * and the history ledger records every event.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AssignmentLifecycleTest {

    @Autowired AssignmentService assignmentService;
    @Autowired HolderRepository holderRepository;
    @Autowired HolderService holderService;
    @Autowired ProductAssignmentRepository assignmentRepository;
    @Autowired AssignmentTransferRepository transferRepository;
    @Autowired ProductRepository productRepository;
    @Autowired UnitRepository unitRepository;
    @Autowired InventoryService inventoryService;

    static Long productId;
    static Long holderAId;
    static Long holderBId;

    static Path tempRoot;

    @DynamicPropertySource
    static void isolatedDataDir(DynamicPropertyRegistry registry) throws Exception {
        tempRoot = Files.createTempDirectory("ims-test-");
        Files.createDirectories(tempRoot.resolve("data"));
        registry.add("app.root", () -> tempRoot.toString());
    }

    @Test
    @Order(1)
    void assignMovesStockOutAndIsRecorded() {
        Unit unit = new Unit();
        unit.setName("A-UNIT"); unit.setSymbol("pc");
        unit.setCreatedAt(java.time.LocalDateTime.now());
        unit.setUpdatedAt(java.time.LocalDateTime.now());
        unitRepository.save(unit);

        Product p = new Product();
        p.setName("Assigned item");
        p.setUnit(unit);
        p.setCurrentStock(BigDecimal.ZERO);
        p.setActive(true);
        p.setCreatedAt(java.time.LocalDateTime.now());
        p.setUpdatedAt(java.time.LocalDateTime.now());
        productId = productRepository.save(p).getId();

        inventoryService.stockIn(new InventoryRequests.StockInRequest(
                productId, new BigDecimal("100"), "REF-A", "opening"));

        Holder a = new Holder();
        a.setName("Alice"); a.setType(Holder.TYPE_USER); a.setActive(true);
        a.setCreatedAt(java.time.LocalDateTime.now());
        a.setUpdatedAt(java.time.LocalDateTime.now());
        Holder b = new Holder();
        b.setName("Site B"); b.setType(Holder.TYPE_PLACE); b.setActive(true);
        b.setCreatedAt(java.time.LocalDateTime.now());
        b.setUpdatedAt(java.time.LocalDateTime.now());
        holderAId = holderRepository.save(a).getId();
        holderBId = holderRepository.save(b).getId();

        AssignmentService.AssignmentDto dto = assignmentService.assign(
                new AssignmentService.AssignRequest(productId, holderAId, new BigDecimal("30"), "field kit"));

        // central stock reduced, assigned quantity visible, history recorded
        assertThat(productRepository.findById(productId).orElseThrow().getCurrentStock())
                .isEqualByComparingTo("70");
        assertThat(dto.quantity()).isEqualByComparingTo("30");
        assertThat(assignmentService.assignedSum(productId)).isEqualByComparingTo("30");
        assertThat(transferRepository.findAll())
                .allMatch(t -> AssignmentTransfer.ASSIGN.equals(t.getAction()));
    }

    @Test
    @Order(2)
    void cannotAssignMoreThanCentralStock() {
        assertThatThrownBy(() -> assignmentService.assign(
                new AssignmentService.AssignRequest(productId, holderBId, new BigDecimal("9999"), "")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INSUFFICIENT_STOCK"));
    }

    @Test
    @Order(3)
    void transferReassignsWithoutTouchingCentralStock() {
        BigDecimal before = productRepository.findById(productId).orElseThrow().getCurrentStock();

        AssignmentService.AssignmentDto dto = assignmentService.transfer(
                new AssignmentService.TransferRequest(productId, holderAId, holderBId, new BigDecimal("10"), "move"));

        assertThat(dto.holderId()).isEqualTo(holderBId);
        assertThat(dto.quantity()).isEqualByComparingTo("10");
        // central stock unchanged, total assigned unchanged, both sides correct
        assertThat(productRepository.findById(productId).orElseThrow().getCurrentStock())
                .isEqualByComparingTo(before);
        assertThat(assignmentService.assignedSum(productId)).isEqualByComparingTo("30");
        assertThat(assignmentRepository.findByProductIdAndHolderId(productId, holderAId).orElseThrow()
                .getQuantity()).isEqualByComparingTo("20");
        assertThat(assignmentRepository.findByProductIdAndHolderId(productId, holderBId).orElseThrow()
                .getQuantity()).isEqualByComparingTo("10");
    }

    @Test
    @Order(4)
    void unassignReturnsStockAndCleansZeroRow() {
        AssignmentService.AssignmentDto dto = assignmentService.unassign(
                new AssignmentService.UnassignRequest(productId, holderAId, new BigDecimal("20"), "returned"));

        // allocation returned -> central stock back to 90 (10 still at Site B),
        // assignment row removed
        assertThat(productRepository.findById(productId).orElseThrow().getCurrentStock())
                .isEqualByComparingTo("90");
        assertThat(assignmentRepository.findByProductIdAndHolderId(productId, holderAId)).isEmpty();
        assertThat(assignmentRepository.findByProductIdAndHolderId(productId, holderAId)
                .map(ProductAssignment::getQuantity).orElse(null)).isNull();
        assertThat(assignmentService.assignedSum(productId)).isEqualByComparingTo("10");
    }

    @Test
    @Order(5)
    void cannotReturnMoreThanHolderHas() {
        // holder A has nothing left; returning more must be rejected
        assertThatThrownBy(() -> assignmentService.unassign(
                new AssignmentService.UnassignRequest(productId, holderAId, new BigDecimal("5"), "")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("NOT_ASSIGNED"));
    }

    @Test
    @Order(6)
    void holderWithAssignmentsCannotBeDeleted() {
        assertThatThrownBy(() -> holderService.delete(holderBId))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("HOLDER_HAS_ASSIGNMENTS"));
    }
}