package lk.ceylonpick.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import lk.ceylonpick.catalog.api.ProductStatus;
import lk.ceylonpick.catalog.domain.Product;
import lk.ceylonpick.catalog.domain.ProductVariant;
import lk.ceylonpick.catalog.repo.ProductRepository;
import lk.ceylonpick.catalog.repo.ProductVariantRepository;
import lk.ceylonpick.orders.api.ActorType;
import lk.ceylonpick.orders.api.OrderStatus;
import lk.ceylonpick.orders.api.PayMethod;
import lk.ceylonpick.orders.api.PlaceOrderCommand;
import lk.ceylonpick.orders.domain.Order;
import lk.ceylonpick.settings.api.SettingKeys;
import lk.ceylonpick.settings.service.SettingsService;
import lk.ceylonpick.shared.Ids;
import lk.ceylonpick.shared.Money;
import lk.ceylonpick.shared.event.OutboxRepository;
import lk.ceylonpick.shared.web.ApiException;
import lk.ceylonpick.vendors.api.VendorStatus;
import lk.ceylonpick.vendors.domain.Vendor;
import lk.ceylonpick.vendors.repo.VendorRepository;

/**
 * Checkout, against the real database.
 *
 * <p>Most of what {@code placeOrder} does is refuse things — BR-05's cap, BR-06's
 * prepaid-only items, the kill switch, a district we do not serve — so most of
 * this file is about the refusals. The one that matters most is BR-19: a
 * two-vendor cart has to become two orders, because every downstream rule
 * (shipping, settlement, vendor payable) assumes one vendor per order.
 */
@SpringBootTest
class PlaceOrderTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private SettingsService settings;
    @Autowired
    private VendorRepository vendors;
    @Autowired
    private ProductRepository products;
    @Autowired
    private ProductVariantRepository variants;
    @Autowired
    private OutboxRepository outbox;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Clock clock;

    private String vendorA;
    private String vendorB;
    /** LKR 1,000 each, COD allowed, in "gifting". */
    private String cheapVariantA;
    private String cheapVariantB;
    /** LKR 4,000, so two of them clear the BR-05 global cap of 5,000. */
    private String pricyVariantA;
    /** Prepaid-only, for BR-06. */
    private String prepaidOnlyVariantA;

    @BeforeEach
    void createCatalog() {
        vendorA = newVendor("Vendor A");
        vendorB = newVendor("Vendor B");
        cheapVariantA = newProductWithVariant(vendorA, "1000.00", false, "01J0000000000000000000CAT1");
        cheapVariantB = newProductWithVariant(vendorB, "1000.00", false, "01J0000000000000000000CAT1");
        pricyVariantA = newProductWithVariant(vendorA, "4000.00", false, "01J0000000000000000000CAT1");
        prepaidOnlyVariantA = newProductWithVariant(vendorA, "500.00", true, "01J0000000000000000000CAT1");
    }

    @AfterEach
    void removeFixture() {
        for (String vendorId : List.of(vendorA, vendorB)) {
            jdbc.update("delete from outbox where aggregate_id in (select id from \"order\" where vendor_id = ?)", vendorId);
            jdbc.update("delete from otp_challenge where order_id in (select id from \"order\" where vendor_id = ?)", vendorId);
            jdbc.update("delete from stock_reservation where order_id in (select id from \"order\" where vendor_id = ?)", vendorId);
            jdbc.update("delete from order_status_history where order_id in (select id from \"order\" where vendor_id = ?)", vendorId);
            jdbc.update("delete from order_item where vendor_id = ?", vendorId);
            jdbc.update("delete from \"order\" where vendor_id = ?", vendorId);
            jdbc.update("delete from product_variant where product_id in (select id from product where vendor_id = ?)", vendorId);
            jdbc.update("delete from product where vendor_id = ?", vendorId);
            jdbc.update("delete from vendor where id = ?", vendorId);
        }
    }

    // ------------------------------------------------------------ the happy path

    /** AT-10 / BR-19: "Two-vendor cart -> two order numbers, two shipments, one confirmation." */
    @Test
    void aCartSpanningTwoVendorsBecomesTwoOrders() {
        var placed = orderService.placeOrder(cod(List.of(
                new PlaceOrderCommand.Line(cheapVariantA, 1),
                new PlaceOrderCommand.Line(cheapVariantB, 1))));

        assertThat(placed.orders()).hasSize(2);
        assertThat(placed.orders()).extracting(Order::getVendorId)
                .containsExactlyInAnyOrder(vendorA, vendorB);
        assertThat(placed.orders()).extracting(Order::getNumber).doesNotHaveDuplicates();
        // Each order carries its own shipping fee — they ship separately.
        assertThat(placed.orders()).allSatisfy(order -> {
            assertThat(order.getShippingFee()).isEqualTo(Money.of("300.00"));
            assertThat(order.getTotal()).isEqualTo(Money.of("1300.00"));
            assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_OTP);
        });
        assertThat(placed.otpChallenges()).as("one OTP per order").hasSize(2);
    }

    /** FR-ORD-03: a COD order goes to AWAITING_OTP, and the code is on its way. */
    @Test
    void aCodOrderAwaitsItsOtp() {
        var placed = orderService.placeOrder(cod(List.of(new PlaceOrderCommand.Line(cheapVariantA, 2))));

        Order order = placed.orders().get(0);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_OTP);
        assertThat(order.getSubtotal()).isEqualTo(Money.of("2000.00"));
        assertThat(order.getNumber()).startsWith("CP-");
        assertThat(placed.otpChallenges().get(0).challengeId()).isNotBlank();

        // FR-ORD-10: the timeline starts with the order coming into existence.
        assertThat(orderService.timelineOf(order.getId()))
                .extracting(h -> h.getToStatus())
                .containsExactly(OrderStatus.PLACED, OrderStatus.AWAITING_OTP);
    }

    /** FR-ORD-04: prepaid waits on the gateway, and no OTP is sent. */
    @Test
    void aPrepaidOrderAwaitsPayment() {
        var placed = orderService.placeOrder(prepaid(List.of(new PlaceOrderCommand.Line(cheapVariantA, 1))));

        assertThat(placed.orders().get(0).getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(placed.otpChallenges()).isEmpty();
    }

    /** The line snapshot is what the order settles on later (SRS §3). */
    @Test
    void linesSnapshotThePriceAndTheCommissions() {
        var placed = orderService.placeOrder(prepaid(List.of(new PlaceOrderCommand.Line(cheapVariantA, 3))));

        var lines = orderService.itemsOf(placed.orders().get(0).getId());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).getUnitPrice()).isEqualTo(Money.of("1000.00"));
        assertThat(lines.get(0).getQty()).isEqualTo(3);
        assertThat(lines.get(0).getPlatformPct()).isEqualByComparingTo("6");   // BR-01
        assertThat(lines.get(0).getCreatorPct()).isEqualByComparingTo("15");   // BR-02
    }

    /** FR-CAT-04: placing an order holds the stock it needs. */
    @Test
    void placingAnOrderReservesItsStock() {
        int before = variants.findById(cheapVariantA).orElseThrow().availableQty();

        orderService.placeOrder(prepaid(List.of(new PlaceOrderCommand.Line(cheapVariantA, 2))));

        assertThat(variants.findById(cheapVariantA).orElseThrow().availableQty()).isEqualTo(before - 2);
    }

    /** Cancelling before confirmation gives the units straight back. */
    @Test
    void cancellingReleasesTheReservedStock() {
        int before = variants.findById(cheapVariantA).orElseThrow().availableQty();
        var placed = orderService.placeOrder(prepaid(List.of(new PlaceOrderCommand.Line(cheapVariantA, 2))));

        orderService.transition(placed.orders().get(0).getId(), OrderStatus.CANCELLED,
                ActorType.SYSTEM, null, "payment timed out", null);

        assertThat(variants.findById(cheapVariantA).orElseThrow().availableQty()).isEqualTo(before);
    }

    /** Architecture §4: the event is written in the same transaction as the change. */
    @Test
    void everyTransitionLeavesAnEventInTheOutbox() {
        var placed = orderService.placeOrder(prepaid(List.of(new PlaceOrderCommand.Line(cheapVariantA, 1))));
        String orderId = placed.orders().get(0).getId();

        var events = outbox.findAll().stream()
                .filter(e -> orderId.equals(e.getAggregateId())).toList();
        assertThat(events).extracting(e -> e.getEventType())
                .containsExactlyInAnyOrder("OrderPlaced", "OrderAwaitingPayment");
        assertThat(events).allSatisfy(e -> {
            assertThat(e.getAggregateType()).isEqualTo("order");
            assertThat(e.getDispatchedAt()).as("nothing has dispatched it yet").isNull();
            assertThat(e.getPayload()).contains(orderId);
        });
    }

    // --------------------------------------------------------------- refusals

    /** AT-11 / BR-05: the global COD cap is LKR 5,000. */
    @Test
    void codIsRefusedAboveTheCap() {
        assertThatThrownBy(() -> orderService.placeOrder(
                cod(List.of(new PlaceOrderCommand.Line(pricyVariantA, 2)))))   // 8,000 + 300
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("COD_CAP_EXCEEDED");
                    assertThat(e.getStatus().value()).isEqualTo(409);
                });
    }

    /** The same cart is fine when it is paid for online. */
    @Test
    void theSameCartIsAllowedWhenPrepaid() {
        var placed = orderService.placeOrder(prepaid(List.of(new PlaceOrderCommand.Line(pricyVariantA, 2))));
        assertThat(placed.orders().get(0).getTotal()).isEqualTo(Money.of("8300.00"));
    }

    /** BR-06: a made-to-order item cannot be sent cash on delivery. */
    @Test
    void codIsRefusedForAPrepaidOnlyItem() {
        assertThatThrownBy(() -> orderService.placeOrder(
                cod(List.of(new PlaceOrderCommand.Line(prepaidOnlyVariantA, 1)))))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("COD_NOT_ALLOWED"));
    }

    /** BR-04: no fee for the district means we do not go there. */
    @Test
    void anUnservedDistrictIsRefused() {
        PlaceOrderCommand command = new PlaceOrderCommand(uniquePhone(), "Test Buyer",
                Map.of("line", "1 Main St"), "Jaffna", "en", PayMethod.COD,
                List.of(new PlaceOrderCommand.Line(cheapVariantA, 1)), null, null, null);

        assertThatThrownBy(() -> orderService.placeOrder(command))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("DISTRICT_NOT_SERVED"));
    }

    /** FR-ORD-14: "Toggle on -> checkout shows 'Ordering paused'." */
    @Test
    void theKillSwitchStopsNewOrders() {
        settings.update(SettingKeys.KILL_SWITCH_NEW_ORDERS, "true", null, null, "test", null);
        try {
            assertThatThrownBy(() -> orderService.placeOrder(
                    cod(List.of(new PlaceOrderCommand.Line(cheapVariantA, 1)))))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo("ORDERING_PAUSED"));
        } finally {
            settings.update(SettingKeys.KILL_SWITCH_NEW_ORDERS, "false", null, null, "test", null);
        }
    }

    @Test
    void anEmptyCartIsRefused() {
        assertThatThrownBy(() -> orderService.placeOrder(cod(List.of())))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("EMPTY_CART"));
    }

    @Test
    void anInvalidPhoneIsRefused() {
        PlaceOrderCommand command = new PlaceOrderCommand("12345", "Test Buyer",
                Map.of(), "Colombo", "en", PayMethod.COD,
                List.of(new PlaceOrderCommand.Line(cheapVariantA, 1)), null, null, null);

        assertThatThrownBy(() -> orderService.placeOrder(command))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INVALID_PHONE"));
    }

    /** A paused product must not be orderable even by variant id. */
    @Test
    void anItemFromAPausedProductIsRefused() {
        Product product = products.findById(
                variants.findById(cheapVariantA).orElseThrow().getProductId()).orElseThrow();
        product.setStatus(ProductStatus.PAUSED);
        products.saveAndFlush(product);

        assertThatThrownBy(() -> orderService.placeOrder(
                prepaid(List.of(new PlaceOrderCommand.Line(cheapVariantA, 1)))))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ITEM_UNAVAILABLE"));
    }

    /**
     * A refusal must leave nothing behind. The second line blows up on the COD
     * cap, and the first line's stock has to come back with the transaction.
     */
    @Test
    void aRefusedCheckoutHoldsNoStock() {
        int before = variants.findById(cheapVariantA).orElseThrow().availableQty();

        assertThatThrownBy(() -> orderService.placeOrder(cod(List.of(
                new PlaceOrderCommand.Line(cheapVariantA, 1),
                new PlaceOrderCommand.Line(pricyVariantA, 2)))))
                .isInstanceOf(ApiException.class);

        assertThat(variants.findById(cheapVariantA).orElseThrow().availableQty())
                .as("the rolled-back transaction released everything")
                .isEqualTo(before);
    }

    // ---------------------------------------------------------------- helpers

    private PlaceOrderCommand cod(List<PlaceOrderCommand.Line> lines) {
        return command(PayMethod.COD, lines);
    }

    private PlaceOrderCommand prepaid(List<PlaceOrderCommand.Line> lines) {
        return command(PayMethod.PREPAID, lines);
    }

    private PlaceOrderCommand command(PayMethod payMethod, List<PlaceOrderCommand.Line> lines) {
        return new PlaceOrderCommand(uniquePhone(), "Test Buyer",
                Map.of("line", "12 Galle Road", "city", "Colombo 03"), "Colombo", "en",
                payMethod, lines, null, null, null);
    }

    /** A fresh number each time, so the BR-07 hourly OTP cap never masks a result. */
    private static String uniquePhone() {
        return "+947" + (10_000_000 + ThreadLocalRandom.current().nextInt(80_000_000));
    }

    private String newVendor(String name) {
        Vendor vendor = new Vendor();
        vendor.setId(Ids.newId());
        vendor.setBusinessName(name);
        vendor.setMakerName(name);
        vendor.setSlug("place-order-" + vendor.getId().toLowerCase());
        vendor.setDistrict("Colombo");
        vendor.setContactPhone("+94770000009");
        vendor.setStatus(VendorStatus.VERIFIED);
        vendor.setCreatedAt(clock.instant());
        return vendors.save(vendor).getId();
    }

    private String newProductWithVariant(String vendorId, String price, boolean prepaidOnly,
                                         String categoryId) {
        Product product = new Product();
        product.setId(Ids.newId());
        product.setVendorId(vendorId);
        product.setCategoryId(categoryId);
        product.setSlug("place-order-" + product.getId().toLowerCase());
        product.setTitle(Map.of("en", "Test product " + price));
        product.setBasePrice(new BigDecimal(price));
        product.setCreatorPct(new BigDecimal("15.00"));
        product.setPrepaidOnly(prepaidOnly);
        product.setStatus(ProductStatus.LIVE);
        product.setCreatedAt(clock.instant());
        products.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setId(Ids.newId());
        variant.setProductId(product.getId());
        variant.setSku("PLACE-ORDER-" + variant.getId());
        variant.setStockQty(100);
        return variants.save(variant).getId();
    }
}
