package lk.ceylonpick.vendors.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lk.ceylonpick.auth.api.AuthUser;
import lk.ceylonpick.shared.web.ApiResponse;
import lk.ceylonpick.vendors.service.VendorService;

/**
 * The signed-in vendor's own record.
 *
 * <p>There is no vendor id in the path anywhere here — it is resolved from the
 * authenticated user, so there is nothing for a caller to substitute. That is
 * the cheapest possible answer to FR-AUTH-03.
 */
@RestController
@RequestMapping("/api/v1/vendor")
@PreAuthorize("hasRole('VENDOR')")
public class VendorProfileController {

    private final VendorService vendors;

    public VendorProfileController(VendorService vendors) {
        this.vendors = vendors;
    }

    @GetMapping("/me")
    public ApiResponse<VendorDtos.VendorView> me(@AuthenticationPrincipal AuthUser principal) {
        var summary = vendors.requireOwnVendor(principal.userId());
        return ApiResponse.ok(VendorDtos.VendorView.of(vendors.require(summary.id())));
    }
}
