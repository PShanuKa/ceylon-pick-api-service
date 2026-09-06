package lk.ceylonpick.auth.web;

import java.util.List;

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

    public AdminUserController(AdminUserService adminUsers, AccountService accounts) {
        this.adminUsers = adminUsers;
        this.accounts = accounts;
    }

    @GetMapping
    public List<AuthResponses.AdminSummary> list() {
        return adminUsers.list().stream()
                .map(p -> new AuthResponses.AdminSummary(p.getUserId(), p.getFullName(), p.getAdminRole()))
                .toList();
    }

    @PostMapping
    public ResponseEntity<AuthResponses.AdminSummary> create(
            @AuthenticationPrincipal AuthUser principal,
            @Valid @RequestBody AuthRequests.CreateAdmin body,
            HttpServletRequest request) {
        AdminRole role = body.adminRole() == null ? AdminRole.MANAGER : body.adminRole();
        AdminProfile created = adminUsers.create(body.email(), body.password(), body.phone(),
                body.fullName(), role, principal.userId(), ClientInfo.ip(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponses.AdminSummary(created.getUserId(), created.getFullName(),
                        created.getAdminRole()));
    }

    @PatchMapping("/{userId}/role")
    public AuthResponses.AdminSummary changeRole(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable String userId,
            @Valid @RequestBody AuthRequests.ChangeAdminRole body,
            HttpServletRequest request) {
        AdminProfile updated = adminUsers.changeRole(userId, body.adminRole(),
                principal.userId(), body.reason(), ClientInfo.ip(request));
        return new AuthResponses.AdminSummary(updated.getUserId(), updated.getFullName(),
                updated.getAdminRole());
    }

    /** Ends every session for the account and stops it signing in again. */
    @PatchMapping("/{userId}/disable")
    public AuthResponses.Message disable(@AuthenticationPrincipal AuthUser principal,
                                         @PathVariable String userId,
                                         HttpServletRequest request) {
        accounts.changeStatus(userId, UserStatus.DISABLED, principal.userId(),
                AdminRole.OWNER.name(), "Disabled by owner", ClientInfo.ip(request));
        return new AuthResponses.Message("Account disabled");
    }

    @PatchMapping("/{userId}/enable")
    public AuthResponses.Message enable(@AuthenticationPrincipal AuthUser principal,
                                        @PathVariable String userId,
                                        HttpServletRequest request) {
        accounts.changeStatus(userId, UserStatus.ACTIVE, principal.userId(),
                AdminRole.OWNER.name(), "Re-enabled by owner", ClientInfo.ip(request));
        return new AuthResponses.Message("Account enabled");
    }
}
