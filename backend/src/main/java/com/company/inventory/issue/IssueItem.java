package com.company.inventory.issue;

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

@Getter
@Setter
@Entity
@Table(name = "issue_items")
public class IssueItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id")
    private Issue issue;

    @ManyToOne(fetch = jakarta.persistence.FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id")
    private com.company.inventory.product.Product product;

    @Column(nullable = false)
    private BigDecimal quantity;
}
