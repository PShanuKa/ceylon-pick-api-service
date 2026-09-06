package lk.ceylonpick.vendors.api;

import java.util.List;
import java.util.Optional;

/** The vendors module's public API (Architecture §4). */
public interface VendorApi {

    Optional<VendorSummary> getVendor(String vendorId);

    Optional<VendorSummary> getVendorBySlug(String slug);

    /**
     * The vendor this person owns, if any.
     *
     * <p>Resolving from the user id rather than carrying a vendor id on the
     * authenticated principal is what keeps auth from depending on this module.
     */
    Optional<VendorSummary> getVendorForUser(String userId);

    /**
     * Throws unless this person owns this vendor. FR-AUTH-03: "vendor A cannot
     * read vendor B's orders via UI or API (403)."
     */
    VendorSummary requireOwned(String userId, String vendorId);

    /** The vendor this person owns, or a 403 if they own none. */
    VendorSummary requireOwnVendor(String userId);

    /**
     * Every vendor currently allowed to sell.
     *
     * <p>Catalog needs this to hide the products of unverified and suspended
     * makers (FR-VEN-02, FR-VEN-09) without joining across the module boundary.
     * Returning the whole set is only reasonable because BR-20 caps phase 1 at
     * 25 vendors; if that cap lifts, replace this with a read model rather than
     * a join (Architecture §4).
     */
    List<String> sellableVendorIds();
}
