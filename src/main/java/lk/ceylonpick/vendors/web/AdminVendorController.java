package lk.ceylonpick.vendors.web;

import java.util.Set;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lk.ceylonpick.auth.api.AuthUser;
import lk.ceylonpick.shared.web.ApiResponse;
import lk.ceylonpick.shared.web.ClientInfo;
import lk.ceylonpick.shared.web.PageQuery;
import lk.ceylonpick.shared.web.PageResponse;
import lk.ceylonpick.vendors.api.VendorStatus;
import lk.ceylonpick.vendors.service.VendorService;

/**
 * The vendor verification queue (UI Spec §8).
 *
 * <p>Open to both admin tiers: verification is daily operational work, not a
 * money or settings change, so it sits on the MANAGER side of the split.
 * {@code hasRole('ADMIN')} is applied to {@code /api/v1/admin/**} by the filter
 * chain.
 */
@RestController
@RequestMapping("/api/v1/admin/vendors")
public class AdminVendorController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "businessName", "district", "status");

    private final VendorService vendors;

    public AdminVendorController(VendorService vendors) {
        this.vendors = vendors;
    }

    /** FR-VEN-01: applications appear here. */
    @GetMapping("/queue")
    public ApiResponse<PageResponse<VendorDtos.VendorView>> queue(PageQuery page) {
        return ApiResponse.ok(PageResponse.of(
                vendors.verificationQueue(page.toPageable(SORTABLE, "createdAt")),
                VendorDtos.VendorView::of));
    }

    @GetMapping
    public ApiResponse<PageResponse<VendorDtos.VendorView>> list(
            @RequestParam(required = false) VendorStatus status, PageQuery page) {
        return ApiResponse.ok(PageResponse.of(
                vendors.list(status, page.toPageable(SORTABLE, "createdAt")),
                VendorDtos.VendorView::of));
    }

    @GetMapping("/{vendorId}")
    public ApiResponse<VendorDtos.VendorView> detail(@PathVariable String vendorId) {
        return ApiResponse.ok(VendorDtos.VendorView.of(vendors.require(vendorId)));
    }

    @GetMapping("/{vendorId}/verification")
    public ApiResponse<VendorDtos.VerificationView> verification(@PathVariable String vendorId) {
        return ApiResponse.ok(VendorDtos.VerificationView.of(vendors.verification(vendorId)));
    }

    /** Records one of the three FR-VEN-02 checks: DOCUMENTS, SAMPLE or CALL. */
    @PostMapping("/{vendorId}/checks/{check}")
    public ApiResponse<VendorDtos.VerificationView> recordCheck(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable String vendorId,
            @PathVariable VendorService.Check check,
            HttpServletRequest request) {
        return ApiResponse.ok(VendorDtos.VerificationView.of(
                vendors.recordCheck(vendorId, check, principal.userId(), ClientInfo.ip(request))));
    }

    /** Refused with 409 until all three checks are recorded. */
    @PostMapping("/{vendorId}/verify")
    public ApiResponse<VendorDtos.VendorView> verify(@AuthenticationPrincipal AuthUser principal,
                                                     @PathVariable String vendorId,
                                                     @Valid @RequestBody VendorDtos.VerifyRequest body,
                                                     HttpServletRequest request) {
        return ApiResponse.ok(VendorDtos.VendorView.of(
                vendors.verify(vendorId, body.loginEmail(), principal.userId(), ClientInfo.ip(request))));
    }

    @PostMapping("/{vendorId}/decline")
    public ApiResponse<VendorDtos.VendorView> decline(@AuthenticationPrincipal AuthUser principal,
                                                      @PathVariable String vendorId,
                                                      @Valid @RequestBody VendorDtos.ReasonRequest body,
                                                      HttpServletRequest request) {
        return ApiResponse.ok(VendorDtos.VendorView.of(
                vendors.decline(vendorId, body.reason(), principal.userId(), ClientInfo.ip(request))));
    }

    /** FR-VEN-09. Products go dark; the login keeps working so open orders ship. */
    @PostMapping("/{vendorId}/suspend")
    public ApiResponse<VendorDtos.VendorView> suspend(@AuthenticationPrincipal AuthUser principal,
                                                      @PathVariable String vendorId,
                                                      @Valid @RequestBody VendorDtos.ReasonRequest body,
                                                      HttpServletRequest request) {
        return ApiResponse.ok(VendorDtos.VendorView.of(
                vendors.suspend(vendorId, body.reason(), principal.userId(), ClientInfo.ip(request))));
    }

    @PostMapping("/{vendorId}/reactivate")
    public ApiResponse<VendorDtos.VendorView> reactivate(@AuthenticationPrincipal AuthUser principal,
                                                         @PathVariable String vendorId,
                                                         HttpServletRequest request) {
        return ApiResponse.ok(VendorDtos.VendorView.of(
                vendors.reactivate(vendorId, principal.userId(), ClientInfo.ip(request))));
    }
}
