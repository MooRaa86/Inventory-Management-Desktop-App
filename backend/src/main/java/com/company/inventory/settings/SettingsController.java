package com.company.inventory.settings;

import com.company.inventory.audit.AuditActions;
import com.company.inventory.audit.AuditService;
import com.company.inventory.common.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final AuditService auditService;

    public record ValueRequest(String value) {
    }

    @GetMapping
    public Map<String, Object> all() {
        return Map.of("editable", settingsService.getAllEditable());
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    public void update(@PathVariable String key, @RequestBody ValueRequest req) {
        try {
            settingsService.set(key, req.value(), true);
        } catch (IllegalArgumentException e) {
            throw new ApiException(422, "INVALID_SETTING", e.getMessage());
        }
        auditService.log(AuditActions.SETTINGS_CHANGE, "setting", key,
                "Setting '" + key + "' changed to '" + req.value() + "'");
    }
}
