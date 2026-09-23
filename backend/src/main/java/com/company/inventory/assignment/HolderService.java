package com.company.inventory.assignment;

import com.company.inventory.common.error.BusinessRuleException;
import com.company.inventory.common.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HolderService {

    private static final List<String> TYPES = List.of(
            Holder.TYPE_USER, Holder.TYPE_DEPARTMENT, Holder.TYPE_PLACE, Holder.TYPE_OTHER);

    private final HolderRepository holderRepository;
    private final ProductAssignmentRepository assignmentRepository;

    public record HolderDto(Long id, String name, String type, String contact, String notes,
                            boolean active, LocalDateTime createdAt, LocalDateTime updatedAt) {

        static HolderDto from(Holder h) {
            return new HolderDto(h.getId(), h.getName(), h.getType(), h.getContact(),
                    h.getNotes(), h.isActive(), h.getCreatedAt(), h.getUpdatedAt());
        }
    }

    public record HolderRequest(
            @jakarta.validation.constraints.NotBlank(message = "Holder name is required")
            @jakarta.validation.constraints.Size(max = 200)
            String name,
            @jakarta.validation.constraints.NotBlank(message = "Holder type is required")
            String type,
            @jakarta.validation.constraints.Size(max = 200) String contact,
            @jakarta.validation.constraints.Size(max = 1000) String notes) {
    }

    @Transactional(readOnly = true)
    public Page<HolderDto> search(String search, String type, Boolean active, int page, int size) {
        String s = normalize(search);
        String t = normalize(type);
        if (t != null && !TYPES.contains(t)) {
            throw new BusinessRuleException("INVALID_HOLDER_TYPE",
                    "Holder type must be one of: USER, DEPARTMENT, PLACE, OTHER.");
        }
        return holderRepository.findAll(
                        holderSpecification(s, t, active),
                        PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.ASC, "name")))
                .map(HolderDto::from);
    }

    @Transactional(readOnly = true)
    public List<HolderDto> all(Boolean active) {
        return holderRepository.findAll(holderSpecification(null, null, active))
                .stream().map(HolderDto::from).toList();
    }

    @Transactional(readOnly = true)
    public HolderDto get(Long id) {
        return HolderDto.from(find(id));
    }

    @Transactional
    public HolderDto create(HolderRequest request) {
        validateType(request.type());
        if (holderRepository.existsByNameIgnoreCase(request.name())) {
            throw new BusinessRuleException("HOLDER_EXISTS",
                    "A holder with this name already exists.");
        }
        Holder holder = new Holder();
        apply(holder, request);
        holder.setActive(true);
        holder.setCreatedAt(LocalDateTime.now());
        holder.setUpdatedAt(LocalDateTime.now());
        return HolderDto.from(holderRepository.save(holder));
    }

    @Transactional
    public HolderDto update(Long id, HolderRequest request) {
        validateType(request.type());
        Holder holder = find(id);
        if (holderRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new BusinessRuleException("HOLDER_EXISTS",
                    "A holder with this name already exists.");
        }
        apply(holder, request);
        holder.setUpdatedAt(LocalDateTime.now());
        return HolderDto.from(holderRepository.save(holder));
    }

    /** Deactivates the holder (preferred when it has assignments). */
    @Transactional
    public void deactivate(Long id) {
        Holder holder = find(id);
        holder.setActive(false);
        holder.setUpdatedAt(LocalDateTime.now());
        holderRepository.save(holder);
    }

    @Transactional
    public void activate(Long id) {
        Holder holder = find(id);
        holder.setActive(true);
        holder.setUpdatedAt(LocalDateTime.now());
        holderRepository.save(holder);
    }

    @Transactional
    public void delete(Long id) {
        Holder holder = find(id);
        long assignments = assignmentRepository.countByHolderId(id);
        if (assignments > 0) {
            throw new BusinessRuleException("HOLDER_HAS_ASSIGNMENTS",
                    "Holder is linked to " + assignments
                            + " product assignment(s). Deactivate instead.");
        }
        holderRepository.delete(holder);
    }

    private org.springframework.data.jpa.domain.Specification<Holder> holderSpecification(
            String search, String type, Boolean active) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (search != null) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), pattern));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private void validateType(String type) {
        if (type == null || !TYPES.contains(type)) {
            throw new BusinessRuleException("INVALID_HOLDER_TYPE",
                    "Holder type must be one of: USER, DEPARTMENT, PLACE, OTHER.");
        }
    }

    private void apply(Holder holder, HolderRequest request) {
        holder.setName(request.name().trim());
        holder.setType(request.type());
        holder.setContact(orEmpty(request.contact()));
        holder.setNotes(orEmpty(request.notes()));
    }

    public Holder find(Long id) {
        return holderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holder not found: " + id));
    }

    private String normalize(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String orEmpty(String s) {
        return s == null ? "" : s;
    }
}