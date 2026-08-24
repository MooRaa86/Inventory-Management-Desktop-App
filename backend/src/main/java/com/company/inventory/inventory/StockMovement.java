package com.company.inventory.inventory;

import com.company.inventory.common.jpa.IsoDateTimeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable ledger entry. Every stock change MUST produce exactly one
 * StockMovement row inside the same transaction as the stock update.
 */
@Getter
@Setter
@Entity
@Table(name = "stock_movements")
public class StockMovement {

    public static final String STOCK_IN = "STOCK_IN";
    public static final String STOCK_OUT = "STOCK_OUT";
    public static final String ADJUSTMENT_IN = "ADJUSTMENT_IN";
    public static final String ADJUSTMENT_OUT = "ADJUSTMENT_OUT";
    public static final String RETURN_IN = "RETURN_IN";
    public static final String RETURN_OUT = "RETURN_OUT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** Denormalized for reporting resilience. */
    @Column(name = "product_sku", nullable = false)
    private String productSku = "";

    @Column(name = "movement_type", nullable = false)
    private String movementType;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "previous_stock", nullable = false)
    private BigDecimal previousStock;

    @Column(name = "new_stock", nullable = false)
    private BigDecimal newStock;

    @Column(nullable = false)
    private String reference = "";

    @Column(nullable = false)
    private String reason = "";

    @Column(nullable = false)
    private String notes = "";

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String username = "";

    @Convert(converter = IsoDateTimeConverter.class)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
