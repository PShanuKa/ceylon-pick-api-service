package lk.ceylonpick.settings.web;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lk.ceylonpick.auth.api.AuthUser;
import lk.ceylonpick.settings.service.SettingsService;
import lk.ceylonpick.shared.web.ApiResponse;
import lk.ceylonpick.shared.web.ClientInfo;

/**
 * FR-ADM-04, restricted to OWNER.
 *
 * <p>These values decide commission, COD caps and payout timing, so they sit on
 * the owner side of the admin split rather than with the daily queues.
 */
@RestController
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasAuthority('ADMIN_OWNER')")
public class AdminSettingsController {

    public record UpdateSetting(@NotBlank String value, String reason) {
    }

    private final SettingsService settings;

    public AdminSettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public ApiResponse<Map<String, String>> all() {
        return ApiResponse.ok(settings.all());
    }

    /** The body carries raw JSON, matching the column: {@code {"value": "6"}}. */
    @PutMapping("/{key}")
    public ApiResponse<Map<String, String>> update(@AuthenticationPrincipal AuthUser principal,
                                                   @PathVariable String key,
                                                   @Valid @RequestBody UpdateSetting body,
                                                   HttpServletRequest request) {
        settings.update(key, body.value(), principal.userId(),
                principal.adminRole() == null ? null : principal.adminRole().name(),
                body.reason(), ClientInfo.ip(request));
        return ApiResponse.ok(settings.all());
    }
}
