package lk.ceylonpick.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import lk.ceylonpick.orders.api.ActorType;
import lk.ceylonpick.orders.api.OrderStatus;
import lk.ceylonpick.shared.web.ApiException;

/**
 * Architecture §13 asks for "unit tests for state machine (every transition,
 * every guard)". The table is small enough to test exhaustively, so this asserts
 * both halves: every listed transition is permitted for its actor, and every
 * unlisted pair is refused for everyone.
 */
class OrderStateMachineTest {

    // ------------------------------------------------------ the table itself

    static List<OrderStateMachine.Transition> table() {
        return OrderStateMachine.table();
    }

    @ParameterizedTest
    @MethodSource("table")
    void everyListedTransitionIsPermittedForItsActors(OrderStateMachine.Transition transition) {
        for (ActorType actor : transition.actors()) {
            assertThat(OrderStateMachine.isAllowed(transition.from(), transition.to(), actor))
                    .as("%s may move %s -> %s", actor, transition.from(), transition.to())
                    .isTrue();
        }
    }

    /** BR-25: an admin may take any move the table contains, and no others. */
    @ParameterizedTest
    @MethodSource("table")
    void adminMayTakeEveryListedTransition(OrderStateMachine.Transition transition) {
        assertThat(OrderStateMachine.isAllowed(transition.from(), transition.to(), ActorType.ADMIN)).isTrue();
    }

    /**
     * The important half. Anything the table does not list is refused for every
     * actor, including admins — "admin overrides must target an allowed next
     * state" (BR-25).
     */
    @Test
    void everyUnlistedPairIsRefusedForEveryActor() {
        Set<String> listed = OrderStateMachine.table().stream()
                .map(t -> t.from() + "->" + t.to())
                .collect(java.util.stream.Collectors.toSet());

        List<String> wronglyAllowed = new ArrayList<>();
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                if (from == to || listed.contains(from + "->" + to)) {
                    continue;
                }
                for (ActorType actor : ActorType.values()) {
                    if (OrderStateMachine.isAllowed(from, to, actor)) {
                        wronglyAllowed.add(actor + ": " + from + " -> " + to);
                    }
                }
            }
        }
        assertThat(wronglyAllowed).isEmpty();
    }

    @Test
    void terminalStatesHaveNoWayOut() {
        for (OrderStatus status : OrderStatus.values()) {
            if (status.isTerminal()) {
                assertThat(OrderStateMachine.allowedTargets(status))
                        .as("%s is terminal", status).isEmpty();
            }
        }
    }

    @Test
    void everyNonTerminalStateCanBeLeft() {
        for (OrderStatus status : OrderStatus.values()) {
            if (!status.isTerminal()) {
                assertThat(OrderStateMachine.allowedTargets(status))
                        .as("%s can be left", status).isNotEmpty();
            }
        }
    }

    /** Terminal is exactly the four Architecture §6 names, no more and no fewer. */
    @Test
    void terminalStatesAreTheFourTheArchitectureNames() {
        assertThat(java.util.Arrays.stream(OrderStatus.values()).filter(OrderStatus::isTerminal).toList())
                .containsExactlyInAnyOrder(OrderStatus.SETTLED, OrderStatus.CANCELLED,
                        OrderStatus.RTO, OrderStatus.REFUNDED);
    }

    // ------------------------------------------------------------ the guards

    /** FR-ORD-11's own example. */
    @Test
    void shippedToConfirmedIsRefusedWithTheAllowedTargets() {
        assertThatThrownBy(() -> OrderStateMachine.assertAllowed(
                OrderStatus.SHIPPED, OrderStatus.CONFIRMED, ActorType.ADMIN, "typo"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(409);
                    assertThat(e.getCode()).isEqualTo("ILLEGAL_TRANSITION");
                    assertThat(e.getDetails()).containsEntry("from", "SHIPPED");
                    assertThat(e.getDetails().get("allowedTargets"))
                            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                            .containsExactlyInAnyOrder("DELIVERED", "RTO");
                });
    }

    /**
     * FR-ORD-04: "CONFIRMED only on a verified PayHere IPN (never from the
     * browser return)." A buyer is not a permitted actor on that edge.
     */
    @Test
    void onlyTheGatewayMayConfirmAPrepaidOrder() {
        assertThat(OrderStateMachine.isAllowed(
                OrderStatus.AWAITING_PAYMENT, OrderStatus.CONFIRMED, ActorType.GATEWAY)).isTrue();
        assertThat(OrderStateMachine.isAllowed(
                OrderStatus.AWAITING_PAYMENT, OrderStatus.CONFIRMED, ActorType.BUYER)).isFalse();
        assertThat(OrderStateMachine.isAllowed(
                OrderStatus.AWAITING_PAYMENT, OrderStatus.CONFIRMED, ActorType.VENDOR)).isFalse();
    }

    /** BR-07: the COD confirmation is the buyer's, not the vendor's. */
    @Test
    void onlyTheBuyerMayConfirmACodOrder() {
        assertThat(OrderStateMachine.isAllowed(
                OrderStatus.AWAITING_OTP, OrderStatus.CONFIRMED, ActorType.BUYER)).isTrue();
        assertThat(OrderStateMachine.isAllowed(
                OrderStatus.AWAITING_OTP, OrderStatus.CONFIRMED, ActorType.VENDOR)).isFalse();
    }

    /** A vendor packs and ships their own orders; they do not decide delivery. */
    @Test
    void aVendorMayPackAndShipButNotDeliver() {
        assertThat(OrderStateMachine.isAllowed(
                OrderStatus.CONFIRMED, OrderStatus.PACKED, ActorType.VENDOR)).isTrue();
        assertThat(OrderStateMachine.isAllowed(
                OrderStatus.PACKED, OrderStatus.SHIPPED, ActorType.VENDOR)).isTrue();
        assertThat(OrderStateMachine.isAllowed(
                OrderStatus.SHIPPED, OrderStatus.DELIVERED, ActorType.VENDOR)).isFalse();
    }

    /** BR-23: a vendor asks to cancel a confirmed order; only an admin does it. */
    @Test
    void cancellingAfterConfirmedIsAdminOnly() {
        assertThat(OrderStateMachine.isAllowed(
                OrderStatus.CONFIRMED, OrderStatus.CANCELLED, ActorType.VENDOR)).isFalse();
        assertThat(OrderStateMachine.isAllowed(
                OrderStatus.CONFIRMED, OrderStatus.CANCELLED, ActorType.ADMIN)).isTrue();
    }

    /** FR-ORD-09: a dispute ends in exactly one of three states. */
    @Test
    void aDisputeResolvesToOneOfThreeOutcomes() {
        assertThat(OrderStateMachine.allowedTargets(OrderStatus.DISPUTED))
                .containsExactlyInAnyOrder(OrderStatus.SETTLED, OrderStatus.REFUNDED, OrderStatus.RTO);
        assertThat(OrderStateMachine.allowedTargets(OrderStatus.DISPUTED, ActorType.BUYER)).isEmpty();
    }

    /** FR-ADM-02: "Reason empty -> transition refused." */
    @Test
    void anAdminOverrideWithoutAReasonIsRefused() {
        assertThatThrownBy(() -> OrderStateMachine.assertAllowed(
                OrderStatus.PACKED, OrderStatus.SHIPPED, ActorType.ADMIN, "  "))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("needs a reason");

        assertThatCode(() -> OrderStateMachine.assertAllowed(
                OrderStatus.PACKED, OrderStatus.SHIPPED, ActorType.ADMIN, "courier API down"))
                .doesNotThrowAnyException();
    }

    /** The same move by its natural actor needs no reason. */
    @Test
    void aVendorShippingNeedsNoReason() {
        assertThatCode(() -> OrderStateMachine.assertAllowed(
                OrderStatus.PACKED, OrderStatus.SHIPPED, ActorType.VENDOR, null))
                .doesNotThrowAnyException();
    }

    @Test
    void repeatingTheCurrentStatusIsANoOpNotATransition() {
        assertThatThrownBy(() -> OrderStateMachine.assertAllowed(
                OrderStatus.CONFIRMED, OrderStatus.CONFIRMED, ActorType.ADMIN, "again"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("TRANSITION_NO_OP"));
    }

    @Test
    void aWrongActorOnARealEdgeIsToldSo() {
        assertThatThrownBy(() -> OrderStateMachine.assertAllowed(
                OrderStatus.CONFIRMED, OrderStatus.PACKED, ActorType.BUYER, null))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("TRANSITION_NOT_YOURS");
                    assertThat(e.getDetails().get("allowedForActor"))
                            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                            .isEmpty();
                });
    }

    // ------------------------------------------------------- status helpers

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void stockIsHeldOnlyBeforeConfirmation(OrderStatus status) {
        boolean expected = status == OrderStatus.PLACED
                || status == OrderStatus.AWAITING_PAYMENT
                || status == OrderStatus.AWAITING_OTP;
        assertThat(status.holdsStockReservation()).isEqualTo(expected);
    }

    @Test
    void cancelledIsNotCountedAsConfirmed() {
        // AT-04: an unconfirmed order "counts as unconfirmed for the creator,
        // not RTO" — so it must never look like a sale.
        assertThat(OrderStatus.CANCELLED.isConfirmedOrLater()).isFalse();
        assertThat(OrderStatus.RTO.isConfirmedOrLater()).isTrue();
        assertThat(OrderStatus.CONFIRMED.isConfirmedOrLater()).isTrue();
    }

    /** The happy path in AT-01, walked one edge at a time. */
    @Test
    void theCodHappyPathIsWalkable() {
        assertThat(walk(OrderStatus.PLACED, List.of(
                new Step(OrderStatus.AWAITING_OTP, ActorType.SYSTEM),
                new Step(OrderStatus.CONFIRMED, ActorType.BUYER),
                new Step(OrderStatus.PACKED, ActorType.VENDOR),
                new Step(OrderStatus.SHIPPED, ActorType.VENDOR),
                new Step(OrderStatus.DELIVERED, ActorType.COURIER),
                new Step(OrderStatus.SETTLED, ActorType.SYSTEM))))
                .isEqualTo(OrderStatus.SETTLED);
    }

    /** AT-02: prepaid, confirmed by the gateway rather than the buyer. */
    @Test
    void thePrepaidHappyPathIsWalkable() {
        assertThat(walk(OrderStatus.PLACED, List.of(
                new Step(OrderStatus.AWAITING_PAYMENT, ActorType.SYSTEM),
                new Step(OrderStatus.CONFIRMED, ActorType.GATEWAY),
                new Step(OrderStatus.PACKED, ActorType.VENDOR),
                new Step(OrderStatus.SHIPPED, ActorType.VENDOR),
                new Step(OrderStatus.DELIVERED, ActorType.COURIER),
                new Step(OrderStatus.SETTLED, ActorType.SYSTEM))))
                .isEqualTo(OrderStatus.SETTLED);
    }

    /** AT-05: the courier brings it back. */
    @Test
    void theRtoPathIsWalkable() {
        assertThat(walk(OrderStatus.PLACED, List.of(
                new Step(OrderStatus.AWAITING_OTP, ActorType.SYSTEM),
                new Step(OrderStatus.CONFIRMED, ActorType.BUYER),
                new Step(OrderStatus.PACKED, ActorType.VENDOR),
                new Step(OrderStatus.SHIPPED, ActorType.VENDOR),
                new Step(OrderStatus.RTO, ActorType.COURIER))))
                .isEqualTo(OrderStatus.RTO);
    }

    /** AT-06: a dispute inside the window, refunded by an admin. */
    @Test
    void theDisputeToRefundPathIsWalkable() {
        assertThat(walk(OrderStatus.DELIVERED, List.of(
                new Step(OrderStatus.DISPUTED, ActorType.BUYER),
                new Step(OrderStatus.REFUNDED, ActorType.ADMIN, "damaged in transit"))))
                .isEqualTo(OrderStatus.REFUNDED);
    }

    /** AT-03: three wrong codes, and the job closes it. */
    @Test
    void theFailedOtpPathIsWalkable() {
        assertThat(walk(OrderStatus.PLACED, List.of(
                new Step(OrderStatus.AWAITING_OTP, ActorType.SYSTEM),
                new Step(OrderStatus.CANCELLED, ActorType.SYSTEM))))
                .isEqualTo(OrderStatus.CANCELLED);
    }

    private record Step(OrderStatus to, ActorType actor, String reason) {

        Step(OrderStatus to, ActorType actor) {
            this(to, actor, null);
        }
    }

    /** Walks a path edge by edge, so a broken table fails on the step that broke. */
    private static OrderStatus walk(OrderStatus start, List<Step> steps) {
        OrderStatus current = start;
        for (Step step : steps) {
            OrderStateMachine.assertAllowed(current, step.to(), step.actor(), step.reason());
            current = step.to();
        }
        return current;
    }
}
