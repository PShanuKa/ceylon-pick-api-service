package lk.ceylonpick.orders.api;

import java.util.List;
import java.util.Map;

/**
 * A checkout, as it arrives from the cart.
 *
 * <p>One command can produce several orders: BR-19 splits a cart spanning N
 * vendors into N orders, "two shipments, one confirmation" (FR-ORD-01).
 *
 * @param buyerPhone   the identity; normalised before use (Architecture §11.1)
 * @param creatorCode  typed at checkout, and overrides the cookie (BR-17)
 * @param cookieCreatorId resolved from the 30-day attribution cookie
 * @param buyerUserId  present only if the buyer happens to be signed in
 */
public record PlaceOrderCommand(
        String buyerPhone,
        String buyerName,
        Map<String, Object> address,
        String district,
        String language,
        PayMethod payMethod,
        List<Line> lines,
        String creatorCode,
        String cookieCreatorId,
        String buyerUserId) {

    public record Line(String variantId, int qty) {
    }
}
