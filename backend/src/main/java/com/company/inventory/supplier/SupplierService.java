package com.company.inventory.supplier;

import com.company.inventory.common.error.BusinessRuleException;
import com.company.inventory.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;

    public record SupplierDto(Long id, String name, String phone, String email, String address,
                              String taxNumber, String notes, boolean active,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {

        static SupplierDto from(Supplier s) {
            return new SupplierDto(s.getId(), s.getName(), s.getPhone(), s.getEmail(),
                    s.getAddress(), s.getTaxNumber(), s.getNotes(), s.isActive(),
                    s.getCreatedAt(), s.getUpdatedAt());
        }
    }

    public record SupplierRequest(
            @jakarta.validation.constraints.NotBlank(message = "Supplier name is required")
            @jakarta.validation.constraints.Size(max = 200)
            String name,
            @jakarta.validation.constraints.Size(max = 40) String phone,
            @jakarta.validation.constraints.Size(max = 200)
            @jakarta.validation.constraints.Pattern(regexp = "^$|^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$",
                    message = "Email format is invalid")
            String email,
            @jakarta.validation.constraints.Size(max = 500) String address,
            @jakarta.validation.constraints.Size(max = 60) String taxNumber,
            @jakarta.validation.constraints.Size(max = 1000) String notes) {
    }

    @Transactional(readOnly = true)
    public Page<SupplierDto> search(String search, Boolean active, int page, int size) {
        String s = normalize(search);
        return supplierRepository.search(s, active,
                        PageRequest.of(page, Math.min(size, 200)))
                .map(SupplierDto::from);
    }

    @Transactional(readOnly = true)
    public SupplierDto get(Long id) {
        return SupplierDto.from(find(id));
    }

    @Transactional
    public SupplierDto create(SupplierRequest request) {
        if (supplierRepository.existsByNameIgnoreCase(request.name())) {
            throw new BusinessRuleException("SUPPLIER_EXISTS",
                    "A supplier with this name already exists.");
        }
        Supplier supplier = new Supplier();
        apply(supplier, request);
        supplier.setActive(true);
        supplier.setCreatedAt(LocalDateTime.now());
        supplier.setUpdatedAt(LocalDateTime.now());
        return SupplierDto.from(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierDto update(Long id, SupplierRequest request) {
        Supplier supplier = find(id);
        if (supplierRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new BusinessRuleException("SUPPLIER_EXISTS",
                    "A supplier with this name already exists.");
        }
        apply(supplier, request);
        supplier.setUpdatedAt(LocalDateTime.now());
        return SupplierDto.from(supplier);
    }

    /** Deactivates the supplier (preferred over delete when purchases exist). */
    @Transactional
    public void deactivate(Long id) {
        Supplier supplier = find(id);
        long inUse = productRepository.countBySupplierId(id);
        if (inUse > 0 && !supplier.isActive()) {
            return;
        }
        supplier.setActive(false);
        supplier.setUpdatedAt(LocalDateTime.now());
        supplierRepository.save(supplier);
    }

    @Transactional
    public void delete(Long id) {
        Supplier supplier = find(id);
        long products = productRepository.countBySupplierId(id);
        if (products > 0) {
            throw new BusinessRuleException("SUPPLIER_IN_USE",
                    "Supplier is linked to " + products + " product(s). Deactivate instead.");
        }
        supplierRepository.delete(supplier);
    }

    private void apply(Supplier supplier, SupplierRequest request) {
        supplier.setName(request.name().trim());
        supplier.setPhone(orEmpty(request.phone()));
        supplier.setEmail(orEmpty(request.email()));
        supplier.setAddress(orEmpty(request.address()));
        supplier.setTaxNumber(orEmpty(request.taxNumber()));
        supplier.setNotes(orEmpty(request.notes()));
    }

    private Supplier find(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new com.company.inventory.common.error.ResourceNotFoundException(
                        "Supplier not found: " + id));
    }

    private String normalize(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
