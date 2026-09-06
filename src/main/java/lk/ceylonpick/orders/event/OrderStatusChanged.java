package lk.ceylonpick.orders.event;

import lk.ceylonpick.orders.api.ActorType;
import lk.ceylonpick.orders.api.OrderStatus;
import lk.ceylonpick.shared.event.DomainEvent;

/**
 * Published in the same transaction as every order transition.
 *
 * <p>One event covers the whole lifecycle rather than a type per transition,
 * because the consumers all branch on the destination anyway: FR-NOT-02 sends a
 * buyer message on CONFIRMED, SHIPPED, DELIVERED and CANCELLED/RTO, and the
 * ledger posts on CONFIRMED, DELIVERED and SETTLED. Adding a state later then
 * costs no new event type.
 */
public record OrderStatusChanged(
        String orderId,
        String orderNumber,
        String vendorId,
        String buyerPhone,
        String language,
        OrderStatus from,
        OrderStatus to,
        ActorType actorType,
        String reason) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "order";
    }

    @Override
    public String aggregateId() {
        return orderId;
    }

    @Override
    public String eventType() {
        return "Order" + capitalised(to);
    }

    /** {@code CONFIRMED} becomes {@code OrderConfirmed}, the naming Architecture §4 uses. */
    private static String capitalised(OrderStatus status) {
        String name = status.name().toLowerCase();
        StringBuilder out = new StringBuilder();
        for (String part : name.split("_")) {
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
