package com.company.inventory.purchase;

import com.company.inventory.common.jpa.IsoLocalDateConverter;
import com.company.inventory.common.jpa.IsoDateTimeConverter;
import com.company.inventory.product.Product;
import com.company.inventory.supplier.Supplier;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "purchases")
public class Purchase {

    public enum Status {PENDING, RECEIVED, CANCELLED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_number", nullable = false, unique = true)
    private String purchaseNumber;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Convert(converter = IsoLocalDateConverter.class)
    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    /** Sum of line totals, in cents. */
    @Column(name = "total_cents", nullable = false)
    private long totalCents = 0L;

    @Column(nullable = false)
    private String notes = "";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by")
    private com.company.inventory.user.User createdBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "received_by")
    private com.company.inventory.user.User receivedBy;

    @Convert(converter = IsoDateTimeConverter.class)
    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseItem> items = new ArrayList<>();

    @Convert(converter = IsoDateTimeConverter.class)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Convert(converter = IsoDateTimeConverter.class)
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void addItem(Product product, java.math.BigDecimal quantity, long unitCostCents) {
        PurchaseItem item = new PurchaseItem();
        item.setPurchase(this);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setUnitCostCents(unitCostCents);
        item.setLineTotalCents(quantity.multiply(java.math.BigDecimal.valueOf(unitCostCents))
                .setScale(0, java.math.RoundingMode.HALF_UP).longValueExact());
        items.add(item);
        totalCents += item.getLineTotalCents();
    }
}
