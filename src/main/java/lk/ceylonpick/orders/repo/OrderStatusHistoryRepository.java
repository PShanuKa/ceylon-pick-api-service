package lk.ceylonpick.orders.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import lk.ceylonpick.orders.domain.OrderStatusHistory;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    /** Insertion order, which is the timeline order the tracking page renders. */
    List<OrderStatusHistory> findByOrderIdOrderByIdAsc(String orderId);
}
