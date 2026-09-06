package lk.ceylonpick.catalog.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import lk.ceylonpick.catalog.domain.ShippingFee;

public interface ShippingFeeRepository extends JpaRepository<ShippingFee, String> {

    List<ShippingFee> findByActiveTrue();
}
