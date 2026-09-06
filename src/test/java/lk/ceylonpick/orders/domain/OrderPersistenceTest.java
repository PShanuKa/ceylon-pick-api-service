package lk.ceylonpick.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.EntityManager;
import lk.ceylonpick.catalog.api.ProductStatus;
import lk.ceylonpick.catalog.domain.Product;
import lk.ceylonpick.catalog.domain.ProductVariant;
import lk.ceylonpick.catalog.repo.ProductRepository;
import lk.ceylonpick.catalog.repo.ProductVariantRepository;
import lk.ceylonpick.orders.api.ActorType;
import lk.ceylonpick.orders.api.OrderStatus;
import lk.ceylonpick.orders.api.PayMethod;
import lk.ceylonpick.orders.repo.OrderItemRepository;
import lk.ceylonpick.orders.repo.OrderRepository;
import lk.ceylonpick.orders.repo.OrderStatusHistoryRepository;
import lk.ceylonpick.orders.service.OrderNumbers;
import lk.ceylonpick.shared.Ids;
import lk.ceylonpick.shared.Money;
import lk.ceylonpick.vendors.api.VendorStatus;
import lk.ceylonpick.vendors.domain.Vendor;
import lk.ceylonpick.vendors.repo.VendorRepository;

/**
 * The mapping does more than validate at startup: it has to round-trip.
 *
 * <p>Worth its own test because three things here are easy to get subtly wrong
 * and impossible to notice from a schema check — {@code Money} passing through a
 * converter, {@code line_total} being computed by the database rather than the
 * application, and the {@code CHECK (total = subtotal + shipping_fee)} that has
 * to hold for every order ever written.
 */
@SpringBootTest
class OrderPersistenceTest {

    @Autowired
    private OrderRepository orders;
    @Autowired
    private OrderItemRepository items;
    @Autowired
    private OrderStatusHistoryRepository history;
    @Autowired
    private OrderNumbers orderNumbers;
    @Autowired
    private VendorRepository vendors;
    @Autowired
    private ProductRepository products;
    @Autowired
    private ProductVariantRepository variants;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Clock clock;

    private String vendorId;
    private String productId;
    private String variantId;

    @BeforeEach
    void createCatalogFixture() {
        Vendor vendor = new Vendor();
        vendor.setId(Ids.newId());
        vendor.setBusinessName("Order Test Maker");
        vendor.setMakerName("Test");
        vendor.setSlug("order-test-" + vendor.getId().toLowerCase());
        vendor.setDistrict("Colombo");
        vendor.setContactPhone("+94770000003");
        vendor.setStatus(VendorStatus.VERIFIED);
        vendor.setCreatedAt(clock.instant());
        vendorId = vendors.save(vendor).getId();

        Product product = new Product();
        product.setId(Ids.newId());
        product.setVendorId(vendorId);
        product.setCategoryId("01J0000000000000000000CAT1");
        product.setSlug("order-test-" + product.getId().toLowerCase());
        product.setTitle(Map.of("en", "Kithul treacle"));
        product.setBasePrice(new BigDecimal("1450.00"));
        product.setCreatorPct(new BigDecimal("15.00"));
        product.setStatus(ProductStatus.LIVE);
        product.setCreatedAt(clock.instant());
        productId = products.save(product).getId();

        ProductVariant variant = new ProductVariant();
        variant.setId(Ids.newId());
        variant.setProductId(productId);
        variant.setSku("ORDER-TEST-" + variant.getId());
        variant.setStockQty(50);
        variantId = variants.save(variant).getId();
    }

    @AfterEach
    void removeFixture() {
        jdbc.update("delete from order_status_history where order_id in "
                + "(select id from \"order\" where vendor_id = ?)", vendorId);
        jdbc.update("delete from order_item where vendor_id = ?", vendorId);
        jdbc.update("delete from \"order\" where vendor_id = ?", vendorId);
        variants.deleteById(variantId);
        products.deleteById(productId);
        vendors.deleteById(vendorId);
    }

    private Order newOrder(Money subtotal, Money shipping) {
        Order order = new Order();
        order.setId(Ids.newId());
        order.setNumber(orderNumbers.next());
        order.setVendorId(vendorId);
        order.setBuyerPhone("+94771112233");
        order.setBuyerName("Test Buyer");
        order.setAddress(Map.of("line", "12 Galle Road", "city", "Colombo 03"));
        order.setDistrict("Colombo");
        order.setPayMethod(PayMethod.COD);
        order.setStatus(OrderStatus.PLACED);
        order.setSubtotal(subtotal);
        order.setShippingFee(shipping);
        order.recalculateTotal();
        order.setPlacedAt(clock.instant());
        return order;
    }

    @Test
    void anOrderRoundTripsWithItsMoneyIntact() {
        Order saved = orders.saveAndFlush(newOrder(Money.of("1450.00"), Money.of("300.00")));
        entityManager.clear();

        Order reloaded = orders.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getSubtotal()).isEqualTo(Money.of("1450.00"));
        assertThat(reloaded.getShippingFee()).isEqualTo(Money.of("300.00"));
        assertThat(reloaded.getTotal()).isEqualTo(Money.of("1750.00"));
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(reloaded.getPayMethod()).isEqualTo(PayMethod.COD);
        assertThat(reloaded.getAddress()).containsEntry("city", "Colombo 03");
        assertThat(reloaded.getVersion()).isZero();
    }

    /** The number a buyer quotes: prefixed, and never handed out twice. */
    @Test
    void orderNumbersAreUniqueAndPrefixed() {
        String first = orderNumbers.next();
        String second = orderNumbers.next();
        assertThat(first).startsWith("CP-");
        assertThat(second).isNotEqualTo(first);
    }

    /** {@code line_total} is GENERATED ALWAYS, so the database computes it, not us. */
    @Test
    void lineTotalIsComputedByTheDatabase() {
        Order order = orders.saveAndFlush(newOrder(Money.of("4350.00"), Money.of("300.00")));

        OrderItem item = new OrderItem();
        item.setId(Ids.newId());
        item.setOrderId(order.getId());
        item.setVariantId(variantId);
        item.setProductId(productId);
        item.setVendorId(vendorId);
        item.setTitleSnapshot("Kithul treacle");
        item.setQty(3);
        item.setUnitPrice(Money.of("1450.00"));
        item.setCreatorPct(new BigDecimal("15.00"));
        item.setPlatformPct(new BigDecimal("6.00"));
        items.saveAndFlush(item);
        entityManager.clear();

        OrderItem reloaded = items.findById(item.getId()).orElseThrow();
        assertThat(reloaded.getLineTotal()).isEqualTo(Money.of("4350.00"));
        assertThat(reloaded.getLineTotal()).isEqualTo(reloaded.computeLineTotal());
    }

    /** The CHECK constraint is the last line of defence against a mispriced order. */
    @Test
    void theDatabaseRefusesATotalThatDoesNotAddUp() {
        Order broken = newOrder(Money.of("1450.00"), Money.of("300.00"));
        broken.setTotal(Money.of("1000.00"));   // deliberately not subtotal + shipping

        assertThatThrownBy(() -> orders.saveAndFlush(broken))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    /** FR-ORD-10: from, to, actor, reason and time, in the order they happened. */
    @Test
    void statusHistoryReadsBackAsATimeline() {
        Order order = orders.saveAndFlush(newOrder(Money.of("1450.00"), Money.of("300.00")));

        history.save(OrderStatusHistory.of(order.getId(), null, OrderStatus.PLACED,
                ActorType.BUYER, null, null, null, clock.instant()));
        history.save(OrderStatusHistory.of(order.getId(), OrderStatus.PLACED, OrderStatus.AWAITING_OTP,
                ActorType.SYSTEM, null, null, Map.of("channel", "SMS"), clock.instant()));
        history.saveAndFlush(OrderStatusHistory.of(order.getId(), OrderStatus.AWAITING_OTP,
                OrderStatus.CONFIRMED, ActorType.BUYER, null, null, null, clock.instant()));
        entityManager.clear();

        List<OrderStatusHistory> timeline = history.findByOrderIdOrderByIdAsc(order.getId());
        assertThat(timeline).extracting(OrderStatusHistory::getToStatus)
                .containsExactly(OrderStatus.PLACED, OrderStatus.AWAITING_OTP, OrderStatus.CONFIRMED);
        assertThat(timeline.get(0).getFromStatus()).as("the first row has nowhere to come from").isNull();
        assertThat(timeline.get(1).getMeta()).containsEntry("channel", "SMS");
        assertThat(timeline.get(2).getActorType()).isEqualTo(ActorType.BUYER);
    }

    /**
     * A transition has to leave behind the timestamp the jobs read. FR-ORD-07
     * finds settlement candidates by {@code delivered_at}, so a DELIVERED order
     * without one would never settle.
     */
    @Test
    void stampingATransitionRecordsItsTimestamp() {
        Order order = newOrder(Money.of("1450.00"), Money.of("300.00"));
        var at = clock.instant();

        order.stampTransitionTo(OrderStatus.CONFIRMED, at);
        assertThat(order.getConfirmedAt()).isEqualTo(at);
        assertThat(order.getClosedAt()).isNull();

        order.stampTransitionTo(OrderStatus.DELIVERED, at);
        assertThat(order.getDeliveredAt()).isEqualTo(at);
        assertThat(order.getClosedAt()).as("DELIVERED is not terminal").isNull();

        order.stampTransitionTo(OrderStatus.SETTLED, at);
        assertThat(order.getSettledAt()).isEqualTo(at);
        assertThat(order.getClosedAt()).as("SETTLED is terminal").isEqualTo(at);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SETTLED);
    }

    /** Optimistic locking is what stops two admins overwriting each other. */
    @Test
    void concurrentEditsAreCaughtByTheVersionColumn() {
        Order order = orders.saveAndFlush(newOrder(Money.of("1450.00"), Money.of("300.00")));
        entityManager.clear();

        Order first = orders.findById(order.getId()).orElseThrow();
        first.setCancelReason("edited by one admin");
        orders.saveAndFlush(first);
        entityManager.clear();

        assertThat(orders.findById(order.getId()).orElseThrow().getVersion())
                .as("the version moves on every write").isEqualTo(1);
    }

    /** The settlement job's query only sees DELIVERED orders past the hold. */
    @Test
    void settlementCandidatesAreFoundByDeliveredAt() {
        Order delivered = newOrder(Money.of("500.00"), Money.of("0.00"));
        var deliveredAt = clock.instant().minusSeconds(8 * 24 * 60 * 60);
        delivered.stampTransitionTo(OrderStatus.DELIVERED, deliveredAt);
        orders.saveAndFlush(delivered);

        Order stillOpen = newOrder(Money.of("500.00"), Money.of("0.00"));
        orders.saveAndFlush(stillOpen);
        entityManager.clear();

        var due = orders.findDueForSettlement(clock.instant().minusSeconds(7 * 24 * 60 * 60),
                org.springframework.data.domain.PageRequest.of(0, 50));
        assertThat(due).extracting(Order::getId).contains(delivered.getId());
        assertThat(due).extracting(Order::getId).doesNotContain(stillOpen.getId());
    }
}
