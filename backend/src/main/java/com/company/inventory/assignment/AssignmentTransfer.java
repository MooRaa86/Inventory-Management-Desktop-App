package com.company.inventory.assignment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Immutable ledger entry recording every assign / unassign / transfer event. */
@Getter
@Setter
@Entity
@Table(name = "assignment_transfers")
public class AssignmentTransfer {

    public static final String ASSIGN = "ASSIGN";
    public static final String UNASSIGN = "UNASSIGN";
    public static final String TRANSFER = "TRANSFER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id")
    private com.company.inventory.product.Product product;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "from_holder_id")
    private Holder fromHolder;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "to_holder_id")
    private Holder toHolder;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String notes = "";

    @Column(name = "transfer_by", nullable = false)
    private String transferBy = "";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}