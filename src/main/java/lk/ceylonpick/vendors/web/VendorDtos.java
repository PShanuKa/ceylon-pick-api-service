package lk.ceylonpick.vendors.web;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lk.ceylonpick.vendors.api.VendorStatus;
import lk.ceylonpick.vendors.domain.Vendor;
import lk.ceylonpick.vendors.domain.VendorVerification;

/** Request and response shapes for the vendors module. */
public final class VendorDtos {

    private VendorDtos() {
    }

    /**
     * FR-VEN-01. Photos and social links are collected by the front end and
     * uploaded to R2 directly; only the resulting document key reaches us.
     */
    public record ApplyRequest(
            @NotBlank @Size(max = 120) String businessName,
            @NotBlank @Size(max = 120) String makerName,
            @NotBlank @Size(max = 60) String district,
            @NotBlank String contactPhone,
            String nicOrBrDocKey,
            @Size(max = 2000) String notes) {
    }

    public record VerifyRequest(@NotBlank @Email String loginEmail) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
    }

    /** What the admin queue and the vendor's own profile show. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VendorView(
            String id,
            String businessName,
            String makerName,
            String slug,
            String district,
            String contactPhone,
            VendorStatus status,
            int strikes,
            Instant verifiedAt,
            Instant createdAt) {

        public static VendorView of(Vendor vendor) {
            return new VendorView(vendor.getId(), vendor.getBusinessName(), vendor.getMakerName(),
                    vendor.getSlug(), vendor.getDistrict(), vendor.getContactPhone(),
                    vendor.getStatus(), vendor.getStrikes(), vendor.getVerifiedAt(), vendor.getCreatedAt());
        }
    }

    /** The public storefront view — no phone, no verification detail. */
    public record PublicVendorView(
            String slug,
            String businessName,
            String makerName,
            String district,
            String story,
            boolean verified) {

        public static PublicVendorView of(Vendor vendor) {
            return new PublicVendorView(vendor.getSlug(), vendor.getBusinessName(), vendor.getMakerName(),
                    vendor.getDistrict(), vendor.getStory(), vendor.getStatus().canSell());
        }
    }

    /** The three FR-VEN-02 checks, as the admin detail screen shows them. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VerificationView(
            String vendorId,
            Instant documentsReceivedAt,
            Instant sampleReceivedAt,
            Instant callCompletedAt,
            boolean complete,
            String reviewerId,
            String notes,
            String declineReason) {

        public static VerificationView of(VendorVerification v) {
            return new VerificationView(v.getVendorId(), v.getDocumentsReceivedAt(), v.getSampleReceivedAt(),
                    v.getCallCompletedAt(), v.isComplete(), v.getReviewerId(), v.getNotes(),
                    v.getDeclineReason());
        }
    }
}
