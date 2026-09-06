package lk.ceylonpick.orders.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import lk.ceylonpick.orders.api.OrderStatus;
import lk.ceylonpick.orders.domain.Order;

public interface OrderRepository extends JpaRepository<Order, String> {

    Optional<Order> findByNumber(String number);

    Page<Order> findByVendorId(String vendorId, Pageable pageable);

    Page<Order> findByVendorIdAndStatus(String vendorId, OrderStatus status, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    /** The buyer's `/me` list, newest first (UI Spec §5). */
    Page<Order> findByBuyerPhoneOrderByPlacedAtDesc(String buyerPhone, Pageable pageable);

    /**
     * FR-ORD-07: DELIVERED orders settle after the BR-10 hold unless a dispute is
     * open. DISPUTED is a different status, so an open dispute simply takes the
     * order out of this result — that is what "pausing settlement" means here.
     */
    @Query("""
            select o from Order o
             where o.status = lk.ceylonpick.orders.api.OrderStatus.DELIVERED
               and o.deliveredAt <= :settleBefore
            """)
    List<Order> findDueForSettlement(@Param("settleBefore") Instant settleBefore, Pageable pageable);

    /** BR-07: COD orders unconfirmed 24 hours after placement are cancelled. */
    @Query("""
            select o from Order o
             where o.status = lk.ceylonpick.orders.api.OrderStatus.AWAITING_OTP
               and o.placedAt <= :cancelBefore
            """)
    List<Order> findExpiredOtpOrders(@Param("cancelBefore") Instant cancelBefore, Pageable pageable);

    /** BR-08: prepaid orders with no successful IPN inside 30 minutes. */
    @Query("""
            select o from Order o
             where o.status = lk.ceylonpick.orders.api.OrderStatus.AWAITING_PAYMENT
               and o.placedAt <= :cancelBefore
            """)
    List<Order> findExpiredPrepaidOrders(@Param("cancelBefore") Instant cancelBefore, Pageable pageable);

    /** BR-18 needs the buyer's phone compared against the creator's at placement. */
    long countByBuyerPhoneAndStatusIn(String buyerPhone, List<OrderStatus> statuses);
}
