package lk.ceylonpick.vendors.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lk.ceylonpick.shared.web.ApiException;
import lk.ceylonpick.shared.web.ApiResponse;
import lk.ceylonpick.shared.web.ClientInfo;
import lk.ceylonpick.vendors.service.VendorService;

/** The "Sell with us" form and the public maker page. No login (UI Spec §1). */
@RestController
@RequestMapping("/api/v1/public/vendors")
public class PublicVendorController {

    private final VendorService vendors;

    public PublicVendorController(VendorService vendors) {
        this.vendors = vendors;
    }

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<Map<String, String>>> apply(
            @Valid @RequestBody VendorDtos.ApplyRequest body, HttpServletRequest request) {
        var vendor = vendors.apply(new VendorService.Application(
                body.businessName(), body.makerName(), body.district(),
                body.contactPhone(), body.nicOrBrDocKey(), body.notes()), ClientInfo.ip(request));
        // The UI promises "We call within 3 working days"; the id lets an
        // applicant quote a reference if they get in touch first.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(Map.of("applicationId", vendor.getId())));
    }

    /** Unverified makers are not public, so this is a 404 until they are verified. */
    @GetMapping("/{slug}")
    public ApiResponse<VendorDtos.PublicVendorView> bySlug(@PathVariable String slug) {
        return vendors.getVendorBySlug(slug)
                .filter(summary -> summary.status().canSell())
                .map(summary -> ApiResponse.ok(VendorDtos.PublicVendorView.of(vendors.require(summary.id()))))
                .orElseThrow(() -> ApiException.notFound("UNKNOWN_VENDOR", "No such maker"));
    }
}
