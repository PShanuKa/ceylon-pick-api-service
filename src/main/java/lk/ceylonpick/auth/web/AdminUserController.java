package lk.ceylonpick.auth.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lk.ceylonpick.auth.api.AdminRole;
import lk.ceylonpick.auth.api.AuthUser;
import lk.ceylonpick.auth.api.UserStatus;
import lk.ceylonpick.auth.domain.AdminProfile;
import lk.ceylonpick.auth.service.AccountService;
import lk.ceylonpick.auth.service.AdminUserService;
import lk.ceylonpick.shared.i18n.MessageResolver;
import lk.ceylonpick.shared.web.ApiResponse;
import lk.ceylonpick.shared.web.ClientInfo;

/**
 * Admin account management, restricted to {@link AdminRole#OWNER}.
 *
 * <p>The OWNER/MANAGER split is an extension: SRS §2.2 has a single flat admin
 * with full access. Everything here — creating admins, changing their tier,
 * disabling accounts — moves money or the rules, so it sits on the OWNER side.
 * The day-to-day queues (orders, disputes, verification, moderation) stay open
 * to both and belong to their own modules.
 *
 * <p>{@code hasRole('ADMIN')} is already enforced for {@code /api/v1/admin/**}
 * by the filter chain; these annotations narrow it further.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasAuthority('ADMIN_OWNER')")
public class AdminUserController {

    private final AdminUserService adminUsers;
    private final AccountService accounts;
    private final MessageResolver messages;

    public AdminUserController(AdminUserService adminUsers, AccountService accounts,
                               MessageResolver messages) {
        this.adminUsers = adminUsers;
        this.accounts = accounts;
        this.messages = messages;
    }

    @GetMapping
    public ApiResponse<List<AuthResponses.AdminSummary>> list() {
        return ApiResponse.ok(adminUsers.list().stream()
                .map(p -> new AuthResponses.AdminSummary(p.getUserId(), p.getFullName(), p.getAdminRole()))
                .toList());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AuthResponses.AdminSummary>> create(
            @AuthenticationPrincipal AuthUser principal,
            @Valid @RequestBody AuthRequests.CreateAdmin body,
            HttpServletRequest request) {
        AdminRole role = body.adminRole() == null ? AdminRole.MANAGER : body.adminRole();
        AdminProfile created = adminUsers.create(body.email(), body.password(), body.phone(),
                body.fullName(), role, principal.userId(), ClientInfo.ip(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(summary(created)));
    }

    @PatchMapping("/{userId}/role")
    public ApiResponse<AuthResponses.AdminSummary> changeRole(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable String userId,
            @Valid @RequestBody AuthRequests.ChangeAdminRole body,
            HttpServletRequest request) {
        return ApiResponse.ok(summary(adminUsers.changeRole(userId, body.adminRole(),
                principal.userId(), body.reason(), ClientInfo.ip(request))));
    }

    /** Ends every session for the account and stops it signing in again. */
    @PatchMapping("/{userId}/disable")
    public ApiResponse<Map<String, String>> disable(@AuthenticationPrincipal AuthUser principal,
                                                    @PathVariable String userId,
                                                    HttpServletRequest request) {
        accounts.changeStatus(userId, UserStatus.DISABLED, principal.userId(),
                AdminRole.OWNER.name(), "Disabled by owner", ClientInfo.ip(request));
        return ApiResponse.message(messages.resolve("message.ACCOUNT_DISABLED"));
    }

    @PatchMapping("/{userId}/enable")
    public ApiResponse<Map<String, String>> enable(@AuthenticationPrincipal AuthUser principal,
                                                   @PathVariable String userId,
                                                   HttpServletRequest request) {
        accounts.changeStatus(userId, UserStatus.ACTIVE, principal.userId(),
                AdminRole.OWNER.name(), "Re-enabled by owner", ClientInfo.ip(request));
        return ApiResponse.message(messages.resolve("message.ACCOUNT_ENABLED"));
    }

    private static AuthResponses.AdminSummary summary(AdminProfile profile) {
        return new AuthResponses.AdminSummary(profile.getUserId(), profile.getFullName(),
                profile.getAdminRole());
    }
}
