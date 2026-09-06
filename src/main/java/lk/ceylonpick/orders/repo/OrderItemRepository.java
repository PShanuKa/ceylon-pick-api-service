package lk.ceylonpick.orders.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import lk.ceylonpick.orders.domain.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, String> {

    List<OrderItem> findByOrderId(String orderId);
}
