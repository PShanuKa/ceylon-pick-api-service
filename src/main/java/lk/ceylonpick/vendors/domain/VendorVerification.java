package lk.ceylonpick.vendors.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The three checks FR-VEN-02 requires before VERIFIED: documents received,
 * sample received, call completed. Project Plan §5.3 spells the same sequence
 * out as "NIC/BR + product samples → 20-minute video call → quality check".
 */
@Entity
@Table(name = "vendor_verification")
@Getter
@Setter
@NoArgsConstructor
public class VendorVerification {

    @Id
    @Column(name = "vendor_id", nullable = false, updatable = false)
    private String vendorId;

    @Column(name = "nic_or_br_doc_key")
    private String nicOrBrDocKey;

    @Column(name = "documents_received_at")
    private Instant documentsReceivedAt;

    @Column(name = "sample_received_at")
    private Instant sampleReceivedAt;

    @Column(name = "call_completed_at")
    private Instant callCompletedAt;

    @Column(name = "reviewer_id")
    private String reviewerId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "decline_reason")
    private String declineReason;

    /** All three checks recorded — the precondition for granting VERIFIED. */
    public boolean isComplete() {
        return documentsReceivedAt != null && sampleReceivedAt != null && callCompletedAt != null;
    }
}
