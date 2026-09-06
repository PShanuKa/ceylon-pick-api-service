package lk.ceylonpick.vendors.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.ceylonpick.auth.api.Role;
import lk.ceylonpick.auth.api.StaffAccounts;
import lk.ceylonpick.settings.api.Settings;
import lk.ceylonpick.shared.Ids;
import lk.ceylonpick.shared.Phones;
import lk.ceylonpick.shared.audit.AuditService;
import lk.ceylonpick.shared.web.ApiException;
import lk.ceylonpick.vendors.api.VendorApi;
import lk.ceylonpick.vendors.api.VendorStatus;
import lk.ceylonpick.vendors.api.VendorSummary;
import lk.ceylonpick.vendors.domain.Vendor;
import lk.ceylonpick.vendors.domain.VendorVerification;
import lk.ceylonpick.vendors.repo.VendorRepository;
import lk.ceylonpick.vendors.repo.VendorVerificationRepository;

/**
 * Vendor applications, verification and suspension.
 *
 * <p>Nothing here touches the auth cache, and that is deliberate. Suspension
 * changes {@code vendor.status}, not {@code app_user.status}: FR-VEN-09 pauses
 * new orders "while existing orders complete", so the vendor must still be able
 * to sign in and ship what they already owe. Public visibility is decided by
 * catalog filtering against {@link #sellableVendorIds()}, read live on each
 * request, so a suspension takes effect immediately.
 */
@Service
public class VendorService implements VendorApi {

    public static final String VENDOR_APPLIED = "VENDOR_APPLIED";
    public static final String VENDOR_CHECK_RECORDED = "VENDOR_CHECK_RECORDED";
    public static final String VENDOR_VERIFIED = "VENDOR_VERIFIED";
    public static final String VENDOR_DECLINED = "VENDOR_DECLINED";
    public static final String VENDOR_SUSPENDED = "VENDOR_SUSPENDED";
    public static final String VENDOR_REACTIVATED = "VENDOR_REACTIVATED";

    /** BR-20 counts everyone occupying a slot, not only those already selling. */
    private static final List<VendorStatus> OCCUPIES_A_SLOT =
            List.of(VendorStatus.APPLIED, VendorStatus.IN_REVIEW, VendorStatus.VERIFIED, VendorStatus.SUSPENDED);

    private final VendorRepository vendors;
    private final VendorVerificationRepository verifications;
    private final StaffAccounts staffAccounts;
    private final Settings settings;
    private final AuditService audit;
    private final Clock clock;

    public VendorService(VendorRepository vendors,
                         VendorVerificationRepository verifications,
                         StaffAccounts staffAccounts,
                         Settings settings,
                         AuditService audit,
                         Clock clock) {
        this.vendors = vendors;
        this.verifications = verifications;
        this.staffAccounts = staffAccounts;
        this.settings = settings;
        this.audit = audit;
        this.clock = clock;
    }

    /** The public application form (FR-VEN-01). */
    public record Application(
            String businessName,
            String makerName,
            String district,
            String contactPhone,
            String nicOrBrDocKey,
            String notes) {
    }

    // -------------------------------------------------------------- read API

    @Override
    @Transactional(readOnly = true)
    public Optional<VendorSummary> getVendor(String vendorId) {
        return vendors.findById(vendorId).map(VendorService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VendorSummary> getVendorBySlug(String slug) {
        return vendors.findBySlug(slug).map(VendorService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VendorSummary> getVendorForUser(String userId) {
        return vendors.findByUserId(userId).map(VendorService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public VendorSummary requireOwned(String userId, String vendorId) {
        VendorSummary vendor = requireOwnVendor(userId);
        if (!vendor.id().equals(vendorId)) {
            // FR-AUTH-03 / AT-19. Same response as "does not exist", so the API
            // cannot be used to enumerate other vendors.
            throw ApiException.forbidden("NOT_YOUR_VENDOR", "You do not have access to this");
        }
        return vendor;
    }

    @Override
    @Transactional(readOnly = true)
    public VendorSummary requireOwnVendor(String userId) {
        return vendors.findByUserId(userId).map(VendorService::toSummary)
                .orElseThrow(() -> ApiException.forbidden("NO_VENDOR_PROFILE",
                        "This account is not linked to a vendor"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> sellableVendorIds() {
        return vendors.findIdsByStatus(VendorStatus.VERIFIED);
    }

    @Transactional(readOnly = true)
    public Page<Vendor> verificationQueue(Pageable pageable) {
        return vendors.findByStatusIn(List.of(VendorStatus.APPLIED, VendorStatus.IN_REVIEW), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Vendor> list(VendorStatus status, Pageable pageable) {
        return status == null ? vendors.findAll(pageable) : vendors.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public Vendor require(String vendorId) {
        return vendors.findById(vendorId)
                .orElseThrow(() -> ApiException.notFound("UNKNOWN_VENDOR", "No such vendor"));
    }

    @Transactional(readOnly = true)
    public VendorVerification verification(String vendorId) {
        return verifications.findById(vendorId)
                .orElseThrow(() -> ApiException.notFound("UNKNOWN_VENDOR", "No such vendor"));
    }

    // ------------------------------------------------------------- lifecycle

    /** FR-VEN-01: "Application appears in admin queue within 1 min." */
    @Transactional
    public Vendor apply(Application application, String ip) {
        String phone = Phones.normalise(application.contactPhone());
        if (phone == null) {
            throw ApiException.badRequest("INVALID_PHONE", "Enter a valid Sri Lankan mobile number");
        }
        int max = settings.vendorCaps().maxVendors();
        if (vendors.countByStatusIn(OCCUPIES_A_SLOT) >= max) {
            throw ApiException.conflict("VENDOR_CAP_REACHED",
                    "We are not taking new makers just now. Phase 1 is capped at " + max + ".", max);
        }

        Instant now = clock.instant();
        Vendor vendor = new Vendor();
        vendor.setId(Ids.newId());
        vendor.setBusinessName(application.businessName());
        vendor.setMakerName(application.makerName());
        vendor.setSlug(uniqueSlug(application.businessName()));
        vendor.setDistrict(application.district());
        vendor.setContactPhone(phone);
        vendor.setStatus(VendorStatus.APPLIED);
        vendor.setCreatedAt(now);
        vendors.save(vendor);

        VendorVerification verification = new VendorVerification();
        verification.setVendorId(vendor.getId());
        verification.setNicOrBrDocKey(application.nicOrBrDocKey());
        verification.setNotes(application.notes());
        verifications.save(verification);

        audit.record(VENDOR_APPLIED, null, null, "vendor", vendor.getId(), ip);
        return vendor;
    }

    /** Which of the three FR-VEN-02 checks an admin is recording. */
    public enum Check {
        DOCUMENTS, SAMPLE, CALL
    }

    @Transactional
    public VendorVerification recordCheck(String vendorId, Check check, String adminId, String ip) {
        Vendor vendor = require(vendorId);
        VendorVerification verification = verification(vendorId);
        Instant now = clock.instant();

        switch (check) {
            case DOCUMENTS -> verification.setDocumentsReceivedAt(now);
            case SAMPLE -> verification.setSampleReceivedAt(now);
            case CALL -> verification.setCallCompletedAt(now);
        }
        verification.setReviewerId(adminId);
        verifications.save(verification);

        if (vendor.getStatus() == VendorStatus.APPLIED) {
            vendor.setStatus(VendorStatus.IN_REVIEW);
            vendors.save(vendor);
        }
        audit.record(VENDOR_CHECK_RECORDED, adminId, Role.ADMIN.name(), "vendor", vendorId, ip,
                null, Map.of("check", check.name()), null);
        return verification;
    }

    /**
     * FR-VEN-02: VERIFIED is granted only once all three checks are recorded.
     * This is also where the vendor first gets a login — applicants have no
     * account until they are approved.
     */
    @Transactional
    public Vendor verify(String vendorId, String loginEmail, String adminId, String ip) {
        Vendor vendor = require(vendorId);
        VendorVerification verification = verification(vendorId);

        if (!verification.isComplete()) {
            throw ApiException.conflict("VERIFICATION_INCOMPLETE",
                    "Record documents, sample and call before verifying");
        }
        if (vendor.getStatus() == VendorStatus.VERIFIED) {
            return vendor;
        }
        if (vendor.getUserId() == null) {
            vendor.setUserId(staffAccounts.provision(Role.VENDOR, loginEmail, vendor.getContactPhone()));
        }
        vendor.setStatus(VendorStatus.VERIFIED);
        vendor.setVerifiedAt(clock.instant());
        vendors.save(vendor);

        audit.record(VENDOR_VERIFIED, adminId, Role.ADMIN.name(), "vendor", vendorId, ip);
        return vendor;
    }

    @Transactional
    public Vendor decline(String vendorId, String reason, String adminId, String ip) {
        Vendor vendor = require(vendorId);
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("REASON_REQUIRED", "A reason is required");
        }
        VendorVerification verification = verification(vendorId);
        verification.setDeclineReason(reason);
        verification.setReviewerId(adminId);
        verifications.save(verification);

        vendor.setStatus(VendorStatus.DECLINED);
        vendors.save(vendor);
        audit.record(VENDOR_DECLINED, adminId, Role.ADMIN.name(), "vendor", vendorId, ip,
                null, null, reason);
        return vendor;
    }

    /**
     * FR-VEN-09. The login stays usable on purpose: new orders stop, but the
     * vendor still has to ship what is already confirmed.
     */
    @Transactional
    public Vendor suspend(String vendorId, String reason, String adminId, String ip) {
        Vendor vendor = require(vendorId);
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("REASON_REQUIRED", "A reason is required");
        }
        VendorStatus previous = vendor.getStatus();
        vendor.setStatus(VendorStatus.SUSPENDED);
        vendors.save(vendor);
        audit.record(VENDOR_SUSPENDED, adminId, Role.ADMIN.name(), "vendor", vendorId, ip,
                Map.of("status", previous.name()), Map.of("status", VendorStatus.SUSPENDED.name()), reason);
        return vendor;
    }

    @Transactional
    public Vendor reactivate(String vendorId, String adminId, String ip) {
        Vendor vendor = require(vendorId);
        if (vendor.getStatus() != VendorStatus.SUSPENDED) {
            throw ApiException.conflict("NOT_SUSPENDED", "This vendor is not suspended");
        }
        vendor.setStatus(VendorStatus.VERIFIED);
        vendors.save(vendor);
        audit.record(VENDOR_REACTIVATED, adminId, Role.ADMIN.name(), "vendor", vendorId, ip);
        return vendor;
    }

    // --------------------------------------------------------------- private

    private String uniqueSlug(String businessName) {
        String base = slugify(businessName);
        if (base.isEmpty()) {
            base = "maker";
        }
        String candidate = base;
        int suffix = 2;
        while (vendors.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private static String slugify(String value) {
        if (value == null) {
            return "";
        }
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private static VendorSummary toSummary(Vendor vendor) {
        return new VendorSummary(vendor.getId(), vendor.getUserId(), vendor.getBusinessName(),
                vendor.getMakerName(), vendor.getSlug(), vendor.getDistrict(), vendor.getStatus());
    }
}
