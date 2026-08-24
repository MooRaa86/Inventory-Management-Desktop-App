package com.company.inventory.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Lob
    @Column(name = "description")
    private String description = "";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private com.company.inventory.category.Category category;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "unit_id")
    private com.company.inventory.unit.Unit unit;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplier_id")
    private com.company.inventory.supplier.Supplier supplier;

    @Column(name = "min_stock", nullable = false)
    private BigDecimal minStock = BigDecimal.ZERO;

    @Column(name = "max_stock")
    private BigDecimal maxStock;

    @Column(name = "current_stock", nullable = false)
    private BigDecimal currentStock = BigDecimal.ZERO;

    /** Price in minor units (cents) to guarantee exact decimal money. */
    @Column(name = "cost_cents", nullable = false)
    private long costCents = 0L;

    /** Price in minor units (cents) to guarantee exact decimal money. */
    @Column(name = "sell_cents", nullable = false)
    private long sellCents = 0L;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
