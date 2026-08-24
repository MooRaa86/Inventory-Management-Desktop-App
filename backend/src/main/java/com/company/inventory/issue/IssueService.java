package com.company.inventory.issue;

import com.company.inventory.audit.AuditActions;
import com.company.inventory.audit.AuditService;
import com.company.inventory.common.error.BusinessRuleException;
import com.company.inventory.common.error.ResourceNotFoundException;
import com.company.inventory.inventory.InventoryService;
import com.company.inventory.inventory.StockMovement;
import com.company.inventory.product.Product;
import com.company.inventory.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueRepository issueRepository;
    private final com.company.inventory.product.ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final AuditService auditService;
    private final UserRepository userRepository;

    public record IssueItemDto(Long id, Long productId, String productName,
                               BigDecimal quantity) {
    }

    public record IssueDto(Long id, String issueNumber, String department, Issue.Status status,
                           String requestedBy, String approvedByName, String completedByName,
                           LocalDateTime completedAt, String notes, String createdByName,
                           LocalDateTime createdAt, List<IssueItemDto> items) {
    }

    public record IssueCreateRequest(
            @jakarta.validation.constraints.NotBlank(message = "Department is required")
            @jakarta.validation.constraints.Size(max = 120)
            String department,
            @jakarta.validation.constraints.Size(max = 120)
            String requestedBy,
            @jakarta.validation.constraints.Size(max = 1000)
            String notes,
            @jakarta.validation.constraints.NotEmpty(message = "At least one item is required")
            @jakarta.validation.Valid
            List<Item> items) {

        public record Item(
                @jakarta.validation.constraints.NotNull Long productId,
                @jakarta.validation.constraints.NotNull BigDecimal quantity) {
        }
    }

    @Transactional(readOnly = true)
    public Page<IssueDto> search(String search, Issue.Status status, String department,
                                 int page, int size) {
        return issueRepository.search(normalize(search), status, normalize(department),
                        PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "id")))
                .map(i -> toDto(i, false));
    }

    @Transactional(readOnly = true)
    public IssueDto get(Long id) {
        return toDto(find(id), true);
    }

    @Transactional
    public IssueDto create(IssueCreateRequest request) {
        validateItems(request.items());
        Issue issue = new Issue();
        issue.setIssueNumber(generateNumber());
        issue.setDepartment(request.department().trim());
        issue.setRequestedBy(orEmpty(request.requestedBy()));
        issue.setNotes(orEmpty(request.notes()));
        issue.setStatus(Issue.Status.DRAFT);
        issue.setCreatedBy(currentUserEntity());
        fillItems(issue, request.items());
        issue.setCreatedAt(LocalDateTime.now());
        issue.setUpdatedAt(LocalDateTime.now());
        Issue saved = issueRepository.save(issue);
        auditService.log(AuditActions.ISSUE_CREATE, "ISSUE", saved.getId(),
                "Created issue " + saved.getIssueNumber() + " for department '"
                        + saved.getDepartment() + "' (" + saved.getItems().size() + " lines)");
        return toDto(saved, true);
    }

    @Transactional
    public IssueDto update(Long id, IssueCreateRequest request) {
        validateItems(request.items());
        Issue issue = find(id);
        requireStatus(issue, Issue.Status.DRAFT, "update");
        issue.setDepartment(request.department().trim());
        issue.setRequestedBy(orEmpty(request.requestedBy()));
        issue.setNotes(orEmpty(request.notes()));
        issue.clearItems();
        fillItems(issue, request.items());
        issue.setUpdatedAt(LocalDateTime.now());
        auditService.log(AuditActions.ISSUE_UPDATE, "ISSUE", issue.getId(),
                "Updated issue " + issue.getIssueNumber());
        return toDto(issueRepository.save(issue), true);
    }

    @Transactional
    public IssueDto approve(Long id) {
        Issue issue = find(id);
        requireStatus(issue, Issue.Status.DRAFT, "approve");
        if (issue.getItems().isEmpty()) {
            throw new BusinessRuleException("EMPTY_ISSUE", "Cannot approve an issue with no items.");
        }
        issue.setStatus(Issue.Status.APPROVED);
        issue.setApprovedBy(currentUserEntity());
        issue.setUpdatedAt(LocalDateTime.now());
        auditService.log(AuditActions.ISSUE_APPROVE, "ISSUE", issue.getId(),
                "Approved issue " + issue.getIssueNumber());
        return toDto(issueRepository.save(issue), true);
    }

    /**
     * Completing an issue deducts stock for every line in ONE transaction and
     * one lock span. If any line lacks availability the whole operation rolls back.
     */
    @Transactional
    public IssueDto complete(Long id) {
        Issue issue = find(id);
        requireStatus(issue, Issue.Status.APPROVED, "complete");

        List<InventoryService.MovementCommand> commands = new ArrayList<>();
        for (IssueItem item : issue.getItems()) {
            commands.add(new InventoryService.MovementCommand(
                    item.getProduct().getId(), StockMovement.STOCK_OUT, item.getQuantity(),
                    issue.getIssueNumber(), "Issue to " + issue.getDepartment(),
                    orEmpty(issue.getNotes())));
        }
        inventoryService.applyMovements(commands);

        issue.setStatus(Issue.Status.COMPLETED);
        issue.setCompletedBy(currentUserEntity());
        issue.setCompletedAt(LocalDateTime.now());
        issue.setUpdatedAt(LocalDateTime.now());
        auditService.log(AuditActions.ISSUE_COMPLETE, "ISSUE", issue.getId(),
                "Completed issue " + issue.getIssueNumber()
                        + " (" + issue.getItems().size() + " lines deducted)");
        return toDto(issueRepository.save(issue), true);
    }

    @Transactional
    public IssueDto cancel(Long id) {
        Issue issue = find(id);
        if (issue.getStatus() != Issue.Status.DRAFT && issue.getStatus() != Issue.Status.APPROVED) {
            throw new BusinessRuleException("INVALID_ISSUE_STATE",
                    "Only DRAFT or APPROVED issues can be cancelled (current: "
                            + issue.getStatus() + ").");
        }
        issue.setStatus(Issue.Status.CANCELLED);
        issue.setUpdatedAt(LocalDateTime.now());
        auditService.log(AuditActions.ISSUE_CANCEL, "ISSUE", issue.getId(),
                "Cancelled issue " + issue.getIssueNumber());
        return toDto(issueRepository.save(issue), true);
    }

    private void fillItems(Issue issue, List<IssueCreateRequest.Item> itemRequests) {
        for (var item : itemRequests) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new BusinessRuleException("PRODUCT_NOT_FOUND",
                            "Product id " + item.productId() + " does not exist."));
            issue.addItem(product, item.quantity());
        }
    }

    private void validateItems(List<IssueCreateRequest.Item> items) {
        for (var item : items) {
            if (item.quantity() == null || item.quantity().signum() <= 0) {
                throw new BusinessRuleException("INVALID_QUANTITY",
                        "Item quantities must be greater than zero.");
            }
            if (item.quantity().stripTrailingZeros().scale() > 3) {
                throw new BusinessRuleException("INVALID_QUANTITY",
                        "Quantities support at most 3 decimal places.");
            }
        }
    }

    private void requireStatus(Issue issue, Issue.Status expected, String action) {
        if (issue.getStatus() != expected) {
            throw new BusinessRuleException("INVALID_ISSUE_STATE",
                    "Cannot " + action + " issue in state " + issue.getStatus()
                            + " (expected " + expected + ").");
        }
    }

    private String generateNumber() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        SecureRandom random = new SecureRandom();
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "ISS-" + datePart + "-" + String.format("%03d", random.nextInt(1000));
            if (!issueRepository.existsByIssueNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique issue number");
    }

    private IssueDto toDto(Issue i, boolean includeItems) {
        var approvedName = i.getApprovedBy() != null ? i.getApprovedBy().getUsername() : null;
        var completedName = i.getCompletedBy() != null ? i.getCompletedBy().getUsername() : null;
        var createdName = i.getCreatedBy() != null ? i.getCreatedBy().getUsername() : null;
        List<IssueItemDto> items = includeItems
                ? i.getItems().stream().map(it -> new IssueItemDto(
                it.getId(), it.getProduct().getId(),
                it.getProduct().getName(), it.getQuantity())).toList()
                : List.of();
        return new IssueDto(i.getId(), i.getIssueNumber(), i.getDepartment(), i.getStatus(),
                i.getRequestedBy(), approvedName, completedName, i.getCompletedAt(),
                i.getNotes(), createdName, i.getCreatedAt(), items);
    }

    private Issue find(Long id) {
        return issueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + id));
    }

    private com.company.inventory.user.User currentUserEntity() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.company.inventory.security.AuthenticatedUser u) {
            return userRepository.findById(u.id()).orElse(null);
        }
        return null;
    }

    private String normalize(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
