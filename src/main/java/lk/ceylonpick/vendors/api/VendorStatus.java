package lk.ceylonpick.vendors.api;

/**
 * FR-VEN-02: a vendor becomes VERIFIED only after documents, sample and call are
 * all recorded. Until then "products of unverified vendors are never publicly
 * visible", which the catalog queries enforce by joining on this column.
 */
public enum VendorStatus {
    /** Applied through the public form; sitting in the admin queue. */
    APPLIED,
    /** An admin has started the checks. */
    IN_REVIEW,
    /** Carries the "Verified Sri Lankan Maker" badge; products may go live. */
    VERIFIED,
    /** FR-VEN-09: products hidden and new orders paused while existing ones complete. */
    SUSPENDED,
    DECLINED;

    /** Only a verified vendor's products may appear publicly or be ordered. */
    public boolean canSell() {
        return this == VERIFIED;
    }
}
