package lk.ceylonpick.orders.api;

import java.util.Optional;

/**
 * Resolves which creator, if any, an order belongs to (BR-17).
 *
 * <p>Declared here and implemented by the creators module rather than the other
 * way round. Orders is what needs the answer, and stating the need as an
 * interface it owns keeps the dependency pointing one way: creators will depend
 * on orders' API, not orders on creators'.
 *
 * <p>Until that module exists, the no-op implementation returns nothing and
 * orders are simply unattributed — which is the correct behaviour for an order
 * placed without any creator involved anyway.
 */
public interface AttributionSource {

    /**
     * @param creatorId    who earns the commission
     * @param type         how they were credited
     * @param selfReferral BR-18: the creator's own phone is the buyer's, so the
     *                     order is "stored without attribution and flagged"
     */
    record Attribution(String creatorId, AttributionType type, boolean selfReferral) {
    }

    /**
     * @param code        a creator code typed at checkout, which overrides the cookie (BR-17)
     * @param cookieCreatorId the creator from a 30-day attribution cookie, last click wins
     * @param buyerPhone  in canonical form, for the BR-18 self-referral check
     */
    Optional<Attribution> resolve(String code, String cookieCreatorId, String buyerPhone);
}
