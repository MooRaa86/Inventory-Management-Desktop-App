package com.company.inventory.unit;

import com.company.inventory.common.error.BusinessRuleException;
import com.company.inventory.common.error.ResourceNotFoundException;
import com.company.inventory.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UnitService {

    private final UnitRepository unitRepository;
    private final ProductRepository productRepository;

    public record UnitDto(Long id, String name, String symbol, boolean active,
                          LocalDateTime createdAt, LocalDateTime updatedAt) {

        static UnitDto from(Unit u) {
            return new UnitDto(u.getId(), u.getName(), u.getSymbol(), u.isActive(),
                    u.getCreatedAt(), u.getUpdatedAt());
        }
    }

    public record UnitRequest(
            @jakarta.validation.constraints.NotBlank(message = "Unit name is required")
            @jakarta.validation.constraints.Size(max = 50)
            String name,
            @jakarta.validation.constraints.NotBlank(message = "Unit symbol is required")
            @jakarta.validation.constraints.Size(max = 10)
            String symbol) {
    }

    @Transactional(readOnly = true)
    public Page<UnitDto> search(String search, Boolean active, int page, int size) {
        String s = normalize(search);
        return unitRepository.search(s, active, PageRequest.of(page, Math.min(size, 200)))
                .map(UnitDto::from);
    }

    @Transactional(readOnly = true)
    public UnitDto get(Long id) {
        return UnitDto.from(find(id));
    }

    @Transactional
    public UnitDto create(UnitRequest request) {
        validateUniqueness(request.name(), request.symbol(), null);
        Unit unit = new Unit();
        apply(unit, request);
        unit.setActive(true);
        unit.setCreatedAt(LocalDateTime.now());
        unit.setUpdatedAt(LocalDateTime.now());
        return UnitDto.from(unitRepository.save(unit));
    }

    @Transactional
    public UnitDto update(Long id, UnitRequest request) {
        Unit unit = find(id);
        validateUniqueness(request.name(), request.symbol(), id);
        apply(unit, request);
        unit.setUpdatedAt(LocalDateTime.now());
        return UnitDto.from(unit);
    }

    @Transactional
    public void delete(Long id) {
        Unit unit = find(id);
        long inUse = productRepository.countByUnitId(id);
        if (inUse > 0) {
            throw new BusinessRuleException("UNIT_IN_USE",
                    "Unit is used by " + inUse + " product(s). Deactivate it instead of deleting.");
        }
        unitRepository.delete(unit);
    }

    private void validateUniqueness(String name, String symbol, Long excludeId) {
        boolean nameTaken = excludeId == null
                ? unitRepository.existsByNameIgnoreCase(name)
                : unitRepository.existsByNameIgnoreCaseAndIdNot(name, excludeId);
        if (nameTaken) {
            throw new BusinessRuleException("UNIT_EXISTS", "A unit with this name already exists.");
        }
        boolean symbolTaken = excludeId == null
                ? unitRepository.existsBySymbolIgnoreCase(symbol)
                : unitRepository.existsBySymbolIgnoreCaseAndIdNot(symbol, excludeId);
        if (symbolTaken) {
            throw new BusinessRuleException("UNIT_SYMBOL_EXISTS",
                    "A unit with this symbol already exists.");
        }
    }

    private void apply(Unit unit, UnitRequest request) {
        unit.setName(request.name().trim());
        unit.setSymbol(request.symbol().trim());
    }

    private Unit find(Long id) {
        return unitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found: " + id));
    }

    private String normalize(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
