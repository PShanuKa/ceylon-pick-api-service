package lk.ceylonpick.catalog.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lk.ceylonpick.catalog.domain.Product;
import lk.ceylonpick.catalog.service.CatalogService;
import lk.ceylonpick.shared.web.ApiResponse;
import lk.ceylonpick.shared.web.PageQuery;
import lk.ceylonpick.shared.web.PageResponse;

/**
 * Public browsing — no login (UI Spec §1). Nothing here exposes a vendor who is
 * not verified, or a product that is not LIVE.
 */
@RestController
@RequestMapping("/api/v1/public/catalog")
public class PublicCatalogController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "basePrice");

    private final CatalogService catalog;

    public PublicCatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/categories")
    public ApiResponse<List<CatalogDtos.CategoryView>> categories() {
        return ApiResponse.ok(catalog.activeCategories().stream()
                .map(CatalogDtos.CategoryView::of).toList());
    }

    /** FR-CAT-01: filters and sort are URL parameters. */
    @GetMapping("/products")
    public ApiResponse<PageResponse<CatalogDtos.ProductCard>> browse(
            @RequestParam(required = false) String category, PageQuery page) {
        return ApiResponse.ok(PageResponse.of(
                catalog.browse(category, page.toPageable(SORTABLE, "createdAt")),
                CatalogDtos.ProductCard::of));
    }

    /** FR-CAT-05. */
    @GetMapping("/search")
    public ApiResponse<PageResponse<CatalogDtos.ProductCard>> search(
            @RequestParam String q, PageQuery page) {
        return ApiResponse.ok(PageResponse.of(
                catalog.search(q, page.toPageable()), CatalogDtos.ProductCard::of));
    }

    /**
     * FR-CAT-03: the delivery estimate and COD availability are on the product
     * page, before the buy action. {@code district} is optional — without it the
     * page shows the cheapest route we serve.
     */
    @GetMapping("/products/{slug}")
    public ApiResponse<CatalogDtos.ProductDetail> product(@PathVariable String slug,
                                                          @RequestParam(required = false) String district) {
        Product product = catalog.publicProductBySlug(slug);
        BigDecimal fee = district == null ? null : catalog.shippingFee(district).orElse(null);
        return ApiResponse.ok(CatalogDtos.ProductDetail.of(product,
                catalog.variantsOf(product.getId()),
                catalog.imagesOf(product.getId()),
                fee,
                false));
    }
}
