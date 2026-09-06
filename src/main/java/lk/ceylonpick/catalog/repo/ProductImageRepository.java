package lk.ceylonpick.catalog.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import lk.ceylonpick.catalog.domain.ProductImage;

public interface ProductImageRepository extends JpaRepository<ProductImage, String> {

    List<ProductImage> findByProductIdOrderBySortOrderAsc(String productId);

    long countByProductId(String productId);
}
