package lk.ceylonpick.orders.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.ceylonpick.auth.api.OrderOtp;
import lk.ceylonpick.catalog.api.CatalogApi;
import lk.ceylonpick.catalog.api.CatalogApi.VariantPricing;
import lk.ceylonpick.orders.api.ActorType;
import lk.ceylonpick.orders.api.AttributionSource;
import lk.ceylonpick.orders.api.AttributionType;
import lk.ceylonpick.orders.api.OrderStatus;
import lk.ceylonpick.orders.api.PayMethod;
import lk.ceylonpick.orders.api.PlaceOrderCommand;
import lk.ceylonpick.orders.domain.Order;
import lk.ceylonpick.orders.domain.OrderItem;
import lk.ceylonpick.orders.domain.OrderStateMachine;
import lk.ceylonpick.orders.domain.OrderStatusHistory;
import lk.ceylonpick.orders.event.OrderStatusChanged;
import lk.ceylonpick.orders.repo.OrderItemRepository;
import lk.ceylonpick.orders.repo.OrderRepository;
import lk.ceylonpick.orders.repo.OrderStatusHistoryRepository;
import lk.ceylonpick.settings.api.SettingKeys;
import lk.ceylonpick.settings.api.Settings;
import lk.ceylonpick.shared.Ids;
import lk.ceylonpick.shared.Money;
import lk.ceylonpick.shared.Phones;
import lk.ceylonpick.shared.event.OutboxPublisher;
import lk.ceylonpick.shared.web.ApiException;

/**
 * Placing orders, and moving them.
 *
 * <p>Architecture §4: "orders is the only module that changes order state." Every
 * transition goes through {@link #transition}, which asks
 * {@link OrderStateMachine} first, writes the history row (FR-ORD-10) and
 * publishes the event in the same transaction — so an order can never move
 * without a record of who moved it or without the consumers hearing about it.
 */
@Service
public class OrderService {

    /** FR-CAT-04: 15 minutes for prepaid, 24 hours for COD. */
    private static final Duration PREPAID_RESERVATION = Duration.ofMinutes(15);
    private static final Duration COD_RESERVATION = Duration.ofHours(24);

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderStatusHistoryRepository history;
    private final OrderNumbers orderNumbers;
    private final CatalogApi catalog;
    private final AttributionSource attribution;
    private final OrderOtp orderOtp;
    private final Settings settings;
    private final OutboxPublisher outbox;
    private final Clock clock;

    public OrderService(OrderRepository orders,
                        OrderItemRepository items,
                        OrderStatusHistoryRepository history,
                        OrderNumbers orderNumbers,
                        CatalogApi catalog,
                        AttributionSource attribution,
                        OrderOtp orderOtp,
                        Settings settings,
                        OutboxPublisher outbox,
                        Clock clock) {
        this.orders = orders;
        this.items = items;
        this.history = history;
        this.orderNumbers = orderNumbers;
        this.catalog = catalog;
        this.attribution = attribution;
        this.orderOtp = orderOtp;
        this.settings = settings;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** What the checkout screen gets back: the orders, and any OTP now pending. */
    public record Placed(List<Order> orders, List<OrderOtp.IssuedOrderOtp> otpChallenges) {
    }

    // ------------------------------------------------------------ place order

    /**
     * FR-ORD-01. A cart spanning N vendors becomes N orders (BR-19), each
     * carrying its own shipping fee and its own OTP or payment intent.
     *
     * <p>One transaction for the whole cart: a buyer who is told two orders were
     * placed must not find that only one of them reserved its stock.
     */
    @Transactional
    public Placed placeOrder(PlaceOrderCommand command) {
        // FR-ORD-14: the kill switch stops new orders while tracking stays up.
        if (settings.killSwitchOn()) {
            throw ApiException.conflict("ORDERING_PAUSED",
                    "Ordering is paused just now. Existing orders are unaffected.");
        }

        String phone = Phones.normalise(command.buyerPhone());
        if (phone == null) {
            throw ApiException.badRequest("INVALID_PHONE", "Enter a valid Sri Lankan mobile number");
        }
        if (command.buyerName() == null || command.buyerName().isBlank()) {
            throw ApiException.badRequest("BUYER_NAME_REQUIRED", "A name is required");
        }
        if (command.lines() == null || command.lines().isEmpty()) {
            throw ApiException.badRequest("EMPTY_CART", "The cart is empty");
        }
        // BR-04: we only deliver where there is a fee.
        Money shippingFee = Money.of(catalog.shippingFee(command.district())
                .orElseThrow(() -> ApiException.badRequest("DISTRICT_NOT_SERVED",
                        "We do not deliver to that district yet")));

        Map<String, List<PricedLine>> byVendor = priceAndGroup(command.lines());
        var attributed = attribution.resolve(command.creatorCode(), command.cookieCreatorId(), phone);

        Instant now = clock.instant();
        List<Order> placed = new ArrayList<>(byVendor.size());
        List<OrderOtp.IssuedOrderOtp> challenges = new ArrayList<>();

        for (var entry : byVendor.entrySet()) {
            Order order = createOrder(command, phone, entry.getKey(), entry.getValue(),
                    shippingFee, attributed.orElse(null), now);
            placed.add(order);

            // PLACED -> AWAITING_OTP or AWAITING_PAYMENT, per Architecture §6.
            if (command.payMethod() == PayMethod.COD) {
                transition(order, OrderStatus.AWAITING_OTP, ActorType.SYSTEM, null, null, null);
                challenges.add(orderOtp.issue(order.getId(), phone));
            } else {
                transition(order, OrderStatus.AWAITING_PAYMENT, ActorType.SYSTEM, null, null, null);
            }
        }
        return new Placed(placed, challenges);
    }

    /** Prices every line against the live catalog, then groups by vendor (BR-19). */
    private Map<String, List<PricedLine>> priceAndGroup(List<PlaceOrderCommand.Line> lines) {
        Map<String, List<PricedLine>> byVendor = new LinkedHashMap<>();
        for (PlaceOrderCommand.Line line : lines) {
            if (line.qty() <= 0) {
                throw ApiException.badRequest("INVALID_QTY", "Quantity must be at least 1");
            }
            VariantPricing pricing = catalog.variantPricing(line.variantId())
                    .orElseThrow(() -> ApiException.badRequest("UNKNOWN_VARIANT",
                            "One of those items is no longer available"));
            if (!pricing.sellable()) {
                // Paused product, or a maker who has since been suspended.
                throw ApiException.conflict("ITEM_UNAVAILABLE",
                        pricing.titleSnapshot() + " is no longer available");
            }
            byVendor.computeIfAbsent(pricing.vendorId(), k -> new ArrayList<>())
                    .add(new PricedLine(pricing, line.qty()));
        }
        return byVendor;
    }

    private Order createOrder(PlaceOrderCommand command, String phone, String vendorId,
                              List<PricedLine> lines, Money shippingFee,
                              AttributionSource.Attribution attributed, Instant now) {
        Money subtotal = Money.ZERO;
        for (PricedLine line : lines) {
            subtotal = subtotal.plus(Money.of(line.pricing().unitPrice()).times(line.qty()));
        }
        assertPaymentMethodAllowed(command.payMethod(), lines, subtotal.plus(shippingFee));

        BigDecimal platformPct = settings.decimal(SettingKeys.PLATFORM_COMMISSION_PCT);

        Order order = new Order();
        order.setId(Ids.newId());
        order.setNumber(orderNumbers.next());
        order.setVendorId(vendorId);
        order.setBuyerUserId(command.buyerUserId());
        order.setBuyerPhone(phone);
        order.setBuyerName(command.buyerName().trim());
        order.setAddress(command.address() == null ? Map.of() : command.address());
        order.setDistrict(command.district());
        order.setLanguage(command.language() == null ? "en" : command.language());
        order.setPayMethod(command.payMethod());
        order.setStatus(OrderStatus.PLACED);
        order.setSubtotal(subtotal);
        order.setShippingFee(shippingFee);
        order.recalculateTotal();
        order.setPlacedAt(now);

        // BR-17: attribution is frozen here and never revisited. BR-18: a
        // self-referral is stored unattributed and flagged, not rejected —
        // the order is legitimate, the commission is not.
        if (attributed != null) {
            order.setSelfReferralFlag(attributed.selfReferral());
            if (!attributed.selfReferral()) {
                order.setCreatorId(attributed.creatorId());
                order.setAttributionType(attributed.type() == null
                        ? AttributionType.LINK : attributed.type());
            }
        }
        orders.save(order);

        for (PricedLine line : lines) {
            OrderItem item = new OrderItem();
            item.setId(Ids.newId());
            item.setOrderId(order.getId());
            item.setVariantId(line.pricing().variantId());
            item.setProductId(line.pricing().productId());
            item.setVendorId(vendorId);
            item.setTitleSnapshot(line.pricing().titleSnapshot());
            item.setQty(line.qty());
            item.setUnitPrice(Money.of(line.pricing().unitPrice()));
            item.setCreatorPct(line.pricing().creatorPct());
            item.setPlatformPct(platformPct);
            items.save(item);

            // FR-CAT-04. Reserved inside this transaction, so a cart whose last
            // line is out of stock leaves nothing held from the earlier ones.
            catalog.reserveStock(order.getId(), line.pricing().variantId(), line.qty(),
                    command.payMethod() == PayMethod.COD ? COD_RESERVATION : PREPAID_RESERVATION);
        }

        recordHistory(order, null, OrderStatus.PLACED, ActorType.BUYER, command.buyerUserId(), null, null, now);
        publish(order, null, OrderStatus.PLACED, ActorType.BUYER, null);
        return order;
    }

    /**
     * FR-ORD-02: "COD shall be hidden with explanation when total exceeds BR-05
     * or any item is prepaid-only (BR-06)."
     *
     * <p>The front end hides the option; this refuses it, because a hidden option
     * is a suggestion and the cap is a rule.
     */
    private void assertPaymentMethodAllowed(PayMethod payMethod, List<PricedLine> lines, Money total) {
        if (payMethod != PayMethod.COD) {
            return;
        }
        for (PricedLine line : lines) {
            if (line.pricing().prepaidOnly() || !line.pricing().codAllowed()) {
                throw ApiException.conflict("COD_NOT_ALLOWED",
                        line.pricing().titleSnapshot() + " must be paid for online");
            }
        }
        Set<String> categories = new LinkedHashSet<>();
        for (PricedLine line : lines) {
            if (line.pricing().categorySlug() != null) {
                categories.add(line.pricing().categorySlug());
            }
        }
        Money cap = Money.of(settings.codCapFor(categories));
        if (total.isGreaterThan(cap)) {
            throw ApiException.conflict("COD_CAP_EXCEEDED",
                    "Cash on delivery is available up to " + cap.display() + " for this order",
                    cap.display());
        }
    }

    // ------------------------------------------------------------ transitions

    /**
     * The one way an order changes state.
     *
     * <p>Takes an id rather than an entity on purpose. An {@code Order} handed
     * back from an earlier transaction is detached, and its {@code @Version} is
     * whatever it was when that transaction ended — saving it here fails with a
     * stale-state error even though nobody else touched the row. Re-reading
     * inside this transaction removes the trap entirely.
     */
    @Transactional
    public Order transition(String orderId, OrderStatus to, ActorType actor, String actorId,
                            String reason, Map<String, Object> meta) {
        return transition(require(orderId), to, actor, actorId, reason, meta);
    }

    /**
     * For callers already inside this transaction holding a managed entity.
     *
     * <p>Order matters: the machine approves the edge first, then the entity is
     * stamped, then history, then the event. Publishing before the check would
     * announce something that never happened; publishing outside the transaction
     * would announce something that might yet roll back.
     */
    @Transactional
    Order transition(Order order, OrderStatus to, ActorType actor, String actorId,
                     String reason, Map<String, Object> meta) {
        OrderStatus from = order.getStatus();
        OrderStateMachine.assertAllowed(from, to, actor, reason);

        Instant now = clock.instant();
        order.stampTransitionTo(to, now);
        if (to == OrderStatus.CANCELLED && reason != null) {
            order.setCancelReason(reason);
        }
        orders.save(order);

        recordHistory(order, from, to, actor, actorId, reason, meta, now);
        publish(order, from, to, actor, reason);

        // FR-CAT-04: stock goes back the moment an order stops being live.
        if (to == OrderStatus.CANCELLED && from.holdsStockReservation()) {
            catalog.releaseStock(order.getId());
        }
        // A confirmed order is a sale; the held units leave stock for good.
        if (to == OrderStatus.CONFIRMED) {
            catalog.consumeStock(order.getId());
        }
        return order;
    }

    @Transactional(readOnly = true)
    public Order require(String orderId) {
        return orders.findById(orderId)
                .orElseThrow(() -> ApiException.notFound("UNKNOWN_ORDER", "No such order"));
    }

    @Transactional(readOnly = true)
    public Order requireByNumber(String number) {
        return orders.findByNumber(number)
                .orElseThrow(() -> ApiException.notFound("UNKNOWN_ORDER", "No such order"));
    }

    @Transactional(readOnly = true)
    public List<OrderItem> itemsOf(String orderId) {
        return items.findByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public List<OrderStatusHistory> timelineOf(String orderId) {
        return history.findByOrderIdOrderByIdAsc(orderId);
    }

    // --------------------------------------------------------------- private

    private void recordHistory(Order order, OrderStatus from, OrderStatus to, ActorType actor,
                               String actorId, String reason, Map<String, Object> meta, Instant at) {
        history.save(OrderStatusHistory.of(order.getId(), from, to, actor, actorId, reason, meta, at));
    }

    private void publish(Order order, OrderStatus from, OrderStatus to, ActorType actor, String reason) {
        outbox.publish(new OrderStatusChanged(order.getId(), order.getNumber(), order.getVendorId(),
                order.getBuyerPhone(), order.getLanguage(), from, to, actor, reason));
    }

    private record PricedLine(VariantPricing pricing, int qty) {
    }
}
