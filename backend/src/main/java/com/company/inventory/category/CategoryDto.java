package com.company.inventory.category;

import com.company.inventory.audit.AuditActions;
import com.company.inventory.audit.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

public record CategoryDto(Long id, String name, String description, boolean active,
                          LocalDateTime createdAt, LocalDateTime updatedAt) {

    static CategoryDto from(Category c) {
        return new CategoryDto(c.getId(), c.getName(), c.getDescription(), c.isActive(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
