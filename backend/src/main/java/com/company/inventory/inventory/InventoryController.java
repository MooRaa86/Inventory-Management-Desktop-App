package com.company.inventory.inventory;

import com.company.inventory.common.web.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final InventoryService inventoryService;

    @PostMapping("/stock-in")
    @PreAuthorize("hasAuthority('STOCK_IN')")
    public InventoryService.MovementDto stockIn(@Valid @RequestBody InventoryRequests.StockInRequest request) {
        return inventoryService.stockIn(request);
    }

    @PostMapping("/stock-out")
    @PreAuthorize("hasAuthority('STOCK_OUT')")
    public InventoryService.MovementDto stockOut(@Valid @RequestBody InventoryRequests.StockOutRequest request) {
        return inventoryService.stockOut(request);
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public InventoryService.MovementDto adjust(@Valid @RequestBody InventoryRequests.AdjustmentRequest request) {
        return inventoryService.adjust(request);
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAuthority('STOCK_VIEW')")
    public PageResponse<InventoryService.MovementDto> movements(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String movementType,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<InventoryService.MovementDto> result = inventoryService.search(
                productId, movementType, username, search,
                parseDate(dateFrom, false), parseDate(dateTo, true), page, size);
        return PageResponse.of(result);
    }

    private LocalDateTime parseDate(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            var date = java.time.LocalDate.parse(value, DATE_ONLY);
            return endOfDay ? date.plusDays(1).atStartOfDay() : date.atStartOfDay();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException e2) {
                throw new com.company.inventory.common.error.BusinessRuleException(
                        "INVALID_DATE", "Dates must use yyyy-MM-dd format.");
            }
        }
    }
}
