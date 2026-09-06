package lk.ceylonpick.vendors.api;

/**
 * What other modules see of a vendor: enough for a product card and an
 * ownership check, and nothing about bank details or verification documents.
 */
public record VendorSummary(
        String id,
        String userId,
        String businessName,
        String makerName,
        String slug,
        String district,
        VendorStatus status) {

    public boolean canSell() {
        return status.canSell();
    }
}
