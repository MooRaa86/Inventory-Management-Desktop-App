package com.company.inventory.issue;

import com.company.inventory.common.jpa.IsoDateTimeConverter;
import com.company.inventory.user.User;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "issues")
public class Issue {

    public enum Status {DRAFT, APPROVED, COMPLETED, CANCELLED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_number", nullable = false, unique = true)
    private String issueNumber;

    @Column(nullable = false)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    /** Free-text requester name captured on the paper request form. */
    @Column(name = "requested_by", nullable = false)
    private String requestedBy = "";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "completed_by")
    private User completedBy;

    @Convert(converter = IsoDateTimeConverter.class)
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(nullable = false)
    private String notes = "";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @OneToMany(mappedBy = "issue", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IssueItem> items = new ArrayList<>();

    @Convert(converter = IsoDateTimeConverter.class)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Convert(converter = IsoDateTimeConverter.class)
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void addItem(com.company.inventory.product.Product product, java.math.BigDecimal quantity) {
        IssueItem item = new IssueItem();
        item.setIssue(this);
        item.setProduct(product);
        item.setQuantity(quantity);
        items.add(item);
    }

    public void clearItems() {
        items.clear();
    }
}
