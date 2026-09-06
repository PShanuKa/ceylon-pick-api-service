package lk.ceylonpick.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A reference to an object in R2. Uploads are pre-signed and go straight from
 * the browser (Architecture §3), so image bytes never pass through this service.
 */
@Entity
@Table(name = "product_image")
@Getter
@Setter
@NoArgsConstructor
public class ProductImage {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private String productId;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "alt")
    private String alt;
}
