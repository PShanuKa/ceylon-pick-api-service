package lk.ceylonpick.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import lk.ceylonpick.catalog.api.ProductStatus;
import lk.ceylonpick.catalog.domain.Product;
import lk.ceylonpick.catalog.domain.ProductVariant;
import lk.ceylonpick.catalog.repo.ProductRepository;
import lk.ceylonpick.catalog.repo.ProductVariantRepository;
import lk.ceylonpick.catalog.repo.StockReservationRepository;
import lk.ceylonpick.shared.Ids;
import lk.ceylonpick.vendors.api.VendorStatus;
import lk.ceylonpick.vendors.domain.Vendor;
import lk.ceylonpick.vendors.repo.VendorRepository;

/**
 * FR-CAT-04: "Two concurrent orders for the last unit: exactly one succeeds."
 *
 * <p>This is the property the whole reservation design exists for, and it cannot
 * be shown with a single-threaded test — a read-then-write implementation passes
 * those and still oversells in production. So this one commits real rows and
 * runs real threads.
 *
 * <p>It uses the configured database rather than Testcontainers, because the
 * guarantee under test is the database's: the availability check lives in the
 * UPDATE's WHERE clause, backed by the {@code reserved_qty <= stock_qty} check
 * constraint. An in-memory substitute would not prove anything.
 */
@SpringBootTest
class StockServiceConcurrencyTest {

    private static final int STOCK = 5;
    private static final int CONCURRENT_BUYERS = 24;

    @Autowired
    private StockService stock;
    @Autowired
    private ProductVariantRepository variants;
    @Autowired
    private ProductRepository products;
    @Autowired
    private VendorRepository vendors;
    @Autowired
    private StockReservationRepository reservations;
    @Autowired
    private Clock clock;

    private String vendorId;
    private String productId;
    private String variantId;

    @BeforeEach
    void createFixture() {
        Vendor vendor = new Vendor();
        vendor.setId(Ids.newId());
        vendor.setBusinessName("Stock Test Maker");
        vendor.setMakerName("Test");
        vendor.setSlug("stock-test-" + vendor.getId().toLowerCase());
        vendor.setDistrict("Colombo");
        vendor.setContactPhone("+94770000001");
        vendor.setStatus(VendorStatus.VERIFIED);
        vendor.setCreatedAt(clock.instant());
        vendorId = vendors.save(vendor).getId();

        Product product = new Product();
        product.setId(Ids.newId());
        product.setVendorId(vendorId);
        product.setCategoryId("01J0000000000000000000CAT1");
        product.setSlug("stock-test-" + product.getId().toLowerCase());
        product.setTitle(Map.of("en", "Stock test"));
        product.setBasePrice(new BigDecimal("100.00"));
        product.setCreatorPct(new BigDecimal("15.00"));
        product.setStatus(ProductStatus.LIVE);
        product.setCreatedAt(clock.instant());
        productId = products.save(product).getId();

        ProductVariant variant = new ProductVariant();
        variant.setId(Ids.newId());
        variant.setProductId(productId);
        variant.setSku("STOCK-TEST-" + variant.getId());
        variant.setStockQty(STOCK);
        variantId = variants.save(variant).getId();
    }

    @AfterEach
    void removeFixture() {
        reservations.deleteAll(reservations.findAll().stream()
                .filter(r -> r.getVariantId().equals(variantId)).toList());
        variants.deleteById(variantId);
        products.deleteById(productId);
        vendors.deleteById(vendorId);
    }

    @Test
    void neverReservesMoreThanTheStockOnHand() throws Exception {
        AtomicInteger reserved = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        CountDownLatch startTogether = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(CONCURRENT_BUYERS);

        try (ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_BUYERS)) {
            for (int i = 0; i < CONCURRENT_BUYERS; i++) {
                String orderId = "TEST-ORDER-" + Ids.newId();
                pool.submit(() -> {
                    try {
                        startTogether.await();
                        stock.reserve(orderId, variantId, 1, Duration.ofMinutes(15));
                        reserved.incrementAndGet();
                    } catch (Exception e) {
                        refused.incrementAndGet();
                    } finally {
                        finished.countDown();
                    }
                });
            }
            startTogether.countDown();
            assertThat(finished.await(60, TimeUnit.SECONDS)).as("all buyers finished").isTrue();
        }

        assertThat(reserved.get()).as("exactly the stock on hand is reserved").isEqualTo(STOCK);
        assertThat(refused.get()).isEqualTo(CONCURRENT_BUYERS - STOCK);

        ProductVariant after = variants.findById(variantId).orElseThrow();
        assertThat(after.getReservedQty()).isEqualTo(STOCK);
        assertThat(after.availableQty()).isZero();
        // The invariant the check constraint protects.
        assertThat(after.getReservedQty()).isLessThanOrEqualTo(after.getStockQty());
    }

    @Test
    void releasingPutsTheUnitsBack() {
        String orderId = "TEST-ORDER-" + Ids.newId();
        stock.reserve(orderId, variantId, 2, Duration.ofMinutes(15));
        assertThat(variants.findById(variantId).orElseThrow().availableQty()).isEqualTo(STOCK - 2);

        stock.release(orderId);

        ProductVariant after = variants.findById(variantId).orElseThrow();
        assertThat(after.getReservedQty()).isZero();
        assertThat(after.getStockQty()).as("a release is not a sale").isEqualTo(STOCK);
    }

    @Test
    void consumingTakesTheUnitsOutOfStockForGood() {
        String orderId = "TEST-ORDER-" + Ids.newId();
        stock.reserve(orderId, variantId, 2, Duration.ofMinutes(15));

        stock.consume(orderId);

        ProductVariant after = variants.findById(variantId).orElseThrow();
        assertThat(after.getReservedQty()).isZero();
        assertThat(after.getStockQty()).as("sold units leave stock").isEqualTo(STOCK - 2);
    }

    /** A second release must not hand the same units back twice. */
    @Test
    void releaseIsIdempotentPerOrder() {
        String orderId = "TEST-ORDER-" + Ids.newId();
        stock.reserve(orderId, variantId, 3, Duration.ofMinutes(15));

        assertThat(stock.release(orderId)).isEqualTo(1);
        assertThat(stock.release(orderId)).as("nothing left open to release").isZero();
        assertThat(variants.findById(variantId).orElseThrow().getReservedQty()).isZero();
    }

    @Test
    void expiredReservationsAreSweptBack() {
        String orderId = "TEST-ORDER-" + Ids.newId();
        var reservation = stock.reserve(orderId, variantId, 2, Duration.ofMinutes(15));
        reservation.setExpiresAt(clock.instant().minusSeconds(1));
        reservations.save(reservation);

        assertThat(stock.releaseExpired()).isGreaterThanOrEqualTo(1);
        assertThat(variants.findById(variantId).orElseThrow().getReservedQty()).isZero();
    }

    @Test
    void reservingMoreThanStockIsRefusedOutright() {
        String orderId = "TEST-ORDER-" + Ids.newId();
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> stock.reserve(orderId, variantId, STOCK + 1, Duration.ofMinutes(15)))
                .hasMessageContaining("no longer available");
        assertThat(variants.findById(variantId).orElseThrow().getReservedQty()).isZero();
        assertThat(reservations.findByOrderIdAndReleasedAtIsNullAndConsumedAtIsNull(orderId)).isEmpty();
    }

    @Test
    void variantsListStaysConsistentAfterMixedTraffic() {
        List<String> orders = List.of("A", "B", "C").stream().map(k -> "TEST-" + k + Ids.newId()).toList();
        stock.reserve(orders.get(0), variantId, 2, Duration.ofMinutes(15));
        stock.reserve(orders.get(1), variantId, 2, Duration.ofMinutes(15));
        stock.consume(orders.get(0));
        stock.release(orders.get(1));

        ProductVariant after = variants.findById(variantId).orElseThrow();
        assertThat(after.getStockQty()).isEqualTo(STOCK - 2);
        assertThat(after.getReservedQty()).isZero();
        assertThat(after.availableQty()).isEqualTo(STOCK - 2);
    }
}
