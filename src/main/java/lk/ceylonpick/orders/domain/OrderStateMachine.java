package lk.ceylonpick.orders.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lk.ceylonpick.orders.api.ActorType;
import lk.ceylonpick.orders.api.OrderStatus;
import lk.ceylonpick.shared.web.ApiException;

/**
 * The order lifecycle, as one explicit table.
 *
 * <p>Architecture §6: "Transitions are implemented in one class
 * (OrderStateMachine) with an explicit transition table; any attempt outside
 * the table throws." BR-25 says the same from the business side: "Order state
 * may change only via transitions in the state machine table; admin overrides
 * must target an allowed next state and record a reason."
 *
 * <p>Deliberately free of Spring and of the database. Every guard that needs
 * data — is there stock, is the photo uploaded, is the signature valid — is the
 * caller's to check; what lives here is the shape of the lifecycle, which is
 * exactly the part that has to be exhaustively tested (NFR-11).
 *
 * <p>Admins are a special case rather than a listed actor on every row. They may
 * trigger any transition the table contains, because couriers and gateways fail
 * and someone has to move the order by hand (the go-live checklist assumes it).
 * What they may not do is invent a transition, and every override needs a reason.
 */
public final class OrderStateMachine {

    /** One row of Architecture §6, Figure 5. */
    public record Transition(OrderStatus from, OrderStatus to, Set<ActorType> actors, String trigger) {
    }

    private static final List<Transition> TABLE = List.of(
            // Prepaid and COD split immediately after checkout.
            new Transition(OrderStatus.PLACED, OrderStatus.AWAITING_PAYMENT,
                    EnumSet.of(ActorType.SYSTEM), "prepaid: create PayHere intent"),
            new Transition(OrderStatus.PLACED, OrderStatus.AWAITING_OTP,
                    EnumSet.of(ActorType.SYSTEM), "COD: send OTP"),

            // FR-ORD-04: only a verified IPN confirms a prepaid order. The browser
            // return URL is not an actor here, and that omission is the requirement.
            new Transition(OrderStatus.AWAITING_PAYMENT, OrderStatus.CONFIRMED,
                    EnumSet.of(ActorType.GATEWAY), "IPN status=2, signature and amount verified"),
            new Transition(OrderStatus.AWAITING_PAYMENT, OrderStatus.CANCELLED,
                    EnumSet.of(ActorType.GATEWAY, ActorType.SYSTEM), "IPN failed, or the BR-08 30-minute job"),

            // BR-07: the buyer confirms; the job or a third wrong code cancels.
            new Transition(OrderStatus.AWAITING_OTP, OrderStatus.CONFIRMED,
                    EnumSet.of(ActorType.BUYER), "correct OTP within attempts and expiry"),
            new Transition(OrderStatus.AWAITING_OTP, OrderStatus.CANCELLED,
                    EnumSet.of(ActorType.SYSTEM), "24-hour job, or 3 failed attempts"),

            // BR-09: no photo, no PACKED.
            new Transition(OrderStatus.CONFIRMED, OrderStatus.PACKED,
                    EnumSet.of(ActorType.VENDOR), "packing photo uploaded"),
            // BR-23: a vendor asks, an admin approves, and it costs a strike.
            new Transition(OrderStatus.CONFIRMED, OrderStatus.CANCELLED,
                    EnumSet.noneOf(ActorType.class), "vendor request approved by admin"),

            new Transition(OrderStatus.PACKED, OrderStatus.SHIPPED,
                    EnumSet.of(ActorType.VENDOR), "tracking number entered"),

            new Transition(OrderStatus.SHIPPED, OrderStatus.DELIVERED,
                    EnumSet.of(ActorType.COURIER), "courier status, poll, or manual entry"),
            new Transition(OrderStatus.SHIPPED, OrderStatus.RTO,
                    EnumSet.of(ActorType.COURIER), "parcel returned"),

            // BR-11 window; an open dispute pauses the BR-10 settlement.
            new Transition(OrderStatus.DELIVERED, OrderStatus.DISPUTED,
                    EnumSet.of(ActorType.BUYER), "dispute opened within 48 hours, with photos"),
            new Transition(OrderStatus.DELIVERED, OrderStatus.SETTLED,
                    EnumSet.of(ActorType.SYSTEM), "settlement job at delivered_at + 7 days, no open dispute"),

            // FR-ORD-09: the three outcomes an admin may decide a dispute to.
            new Transition(OrderStatus.DISPUTED, OrderStatus.SETTLED,
                    EnumSet.noneOf(ActorType.class), "dispute rejected"),
            new Transition(OrderStatus.DISPUTED, OrderStatus.REFUNDED,
                    EnumSet.noneOf(ActorType.class), "prepaid refund"),
            new Transition(OrderStatus.DISPUTED, OrderStatus.RTO,
                    EnumSet.noneOf(ActorType.class), "COD return"));

    private static final Map<OrderStatus, Set<OrderStatus>> TARGETS = buildTargets();

    private OrderStateMachine() {
    }

    /** Every state reachable from {@code from}, in declaration order. */
    public static Set<OrderStatus> allowedTargets(OrderStatus from) {
        return TARGETS.getOrDefault(from, Set.of());
    }

    /** What this particular actor may do from here — the admin dropdown, or a vendor's buttons. */
    public static Set<OrderStatus> allowedTargets(OrderStatus from, ActorType actor) {
        Set<OrderStatus> targets = EnumSet.noneOf(OrderStatus.class);
        for (Transition transition : TABLE) {
            if (transition.from() == from && permits(transition, actor)) {
                targets.add(transition.to());
            }
        }
        return targets;
    }

    public static boolean isAllowed(OrderStatus from, OrderStatus to, ActorType actor) {
        return find(from, to).filter(t -> permits(t, actor)).isPresent();
    }

    /**
     * Throws unless this actor may make this move.
     *
     * <p>FR-ORD-11: "API attempt SHIPPED → CONFIRMED returns 409 with allowed
     * targets." The targets travel in the error's {@code details} so a client can
     * render the valid options instead of parsing prose.
     */
    public static void assertAllowed(OrderStatus from, OrderStatus to, ActorType actor, String reason) {
        if (from == to) {
            throw conflict("TRANSITION_NO_OP", "The order is already " + to, from, actor);
        }
        var transition = find(from, to);
        if (transition.isEmpty()) {
            throw conflict("ILLEGAL_TRANSITION",
                    "An order cannot move from " + from + " to " + to, from, actor);
        }
        if (!permits(transition.get(), actor)) {
            throw conflict("TRANSITION_NOT_YOURS",
                    "A " + actor + " cannot move an order from " + from + " to " + to, from, actor);
        }
        // BR-25 and FR-ADM-02: "Reason empty -> transition refused."
        if (requiresReason(from, to, actor) && (reason == null || reason.isBlank())) {
            throw conflict("TRANSITION_REASON_REQUIRED",
                    "This change needs a reason", from, actor);
        }
    }

    /**
     * An admin override always needs one (BR-25), and so does a cancellation
     * after CONFIRMED whoever asks, because it reverses money and costs the
     * vendor a strike (BR-23).
     */
    public static boolean requiresReason(OrderStatus from, OrderStatus to, ActorType actor) {
        if (actor == ActorType.ADMIN) {
            return true;
        }
        return from == OrderStatus.CONFIRMED && to == OrderStatus.CANCELLED;
    }

    /** The transitions, for documentation and for tests that assert the table itself. */
    public static List<Transition> table() {
        return TABLE;
    }

    // --------------------------------------------------------------- private

    private static java.util.Optional<Transition> find(OrderStatus from, OrderStatus to) {
        return TABLE.stream().filter(t -> t.from() == from && t.to() == to).findFirst();
    }

    /**
     * Admins are permitted everywhere in the table. Some rows list no natural
     * actor at all — a dispute outcome, or a cancellation after CONFIRMED — and
     * those are admin-only by construction.
     */
    private static boolean permits(Transition transition, ActorType actor) {
        return actor == ActorType.ADMIN || transition.actors().contains(actor);
    }

    private static ApiException conflict(String code, String message, OrderStatus from, ActorType actor) {
        return new ApiException(org.springframework.http.HttpStatus.CONFLICT, code, message,
                Map.of("from", from.name(),
                        "allowedTargets", allowedTargets(from).stream().map(Enum::name).toList(),
                        "allowedForActor", allowedTargets(from, actor).stream().map(Enum::name).toList()));
    }

    private static Map<OrderStatus, Set<OrderStatus>> buildTargets() {
        Map<OrderStatus, Set<OrderStatus>> map = new EnumMap<>(OrderStatus.class);
        for (Transition transition : TABLE) {
            map.computeIfAbsent(transition.from(), k -> EnumSet.noneOf(OrderStatus.class))
                    .add(transition.to());
        }
        return Map.copyOf(map);
    }
}
