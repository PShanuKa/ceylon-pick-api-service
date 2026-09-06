package lk.ceylonpick.catalog.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.ceylonpick.catalog.api.CatalogApi;
import lk.ceylonpick.catalog.domain.Category;
import lk.ceylonpick.catalog.domain.Product;
import lk.ceylonpick.catalog.domain.ProductImage;
import lk.ceylonpick.catalog.domain.ProductVariant;
import lk.ceylonpick.catalog.repo.CategoryRepository;
import lk.ceylonpick.catalog.repo.ProductImageRepository;
import lk.ceylonpick.catalog.repo.ProductRepository;
import lk.ceylonpick.catalog.repo.ProductVariantRepository;
import lk.ceylonpick.catalog.repo.ShippingFeeRepository;
import lk.ceylonpick.shared.web.ApiException;
import lk.ceylonpick.vendors.api.VendorApi;

/**
 * Public browsing, and the API other modules read the catalog through.
 *
 * <p>Every public query is scoped to {@link VendorApi#sellableVendorIds()}, so
 * a maker who is unverified or suspended disappears from the storefront on the
 * next request — no cache to wait out.
 */
@Service
public class CatalogService implements CatalogApi {

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ProductImageRepository images;
    private final CategoryRepository categories;
    private final ShippingFeeRepository shippingFees;
    private final VendorApi vendors;
    private final StockService stock;

    public CatalogService(ProductRepository products,
                          ProductVariantRepository variants,
                          ProductImageRepository images,
                          CategoryRepository categories,
                          ShippingFeeRepository shippingFees,
                          VendorApi vendors,
                          StockService stock) {
        this.products = products;
        this.variants = variants;
        this.images = images;
        this.categories = categories;
        this.shippingFees = shippingFees;
        this.vendors = vendors;
        this.stock = stock;
    }

    // ------------------------------------------------------------- public browse

    @Transactional(readOnly = true)
    public List<Category> activeCategories() {
        return categories.findByActiveTrueOrderBySortOrderAsc();
    }

    /** FR-CAT-01: only LIVE products of VERIFIED vendors. */
    @Transactional(readOnly = true)
    public Page<Product> browse(String categorySlug, Pageable pageable) {
        List<String> sellable = vendors.sellableVendorIds();
        if (sellable.isEmpty()) {
            return Page.empty(pageable);
        }
        String categoryId = null;
        if (categorySlug != null && !categorySlug.isBlank()) {
            categoryId = categories.findBySlug(categorySlug)
                    .orElseThrow(() -> ApiException.notFound("UNKNOWN_CATEGORY", "No such category"))
                    .getId();
        }
        return products.findLive(sellable, categoryId, pageable);
    }

    /** FR-CAT-05. */
    @Transactional(readOnly = true)
    public Page<Product> search(String query, Pageable pageable) {
        List<String> sellable = vendors.sellableVendorIds();
        if (sellable.isEmpty() || query == null || query.isBlank()) {
            return Page.empty(pageable);
        }
        String term = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        return products.search(sellable, term, pageable);
    }

    /** The public product page. A paused or unverified maker's product is a 404. */
    @Transactional(readOnly = true)
    public Product publicProductBySlug(String slug) {
        Product product = products.findBySlug(slug)
                .orElseThrow(() -> ApiException.notFound("UNKNOWN_PRODUCT", "No such product"));
        boolean visible = product.getStatus().isPubliclyVisible()
                && vendors.getVendor(product.getVendorId()).map(v -> v.canSell()).orElse(false);
        if (!visible) {
            throw ApiException.notFound("UNKNOWN_PRODUCT", "No such product");
        }
        return product;
    }

    @Transactional(readOnly = true)
    public List<ProductVariant> variantsOf(String productId) {
        return variants.findByProductIdOrderBySku(productId);
    }

    @Transactional(readOnly = true)
    public List<ProductImage> imagesOf(String productId) {
        return images.findByProductIdOrderBySortOrderAsc(productId);
    }

    // ---------------------------------------------------------------- module API

    @Override
    @Transactional(readOnly = true)
    public Optional<VariantPricing> variantPricing(String variantId) {
        return variants.findById(variantId).flatMap(variant ->
                products.findById(variant.getProductId()).map(product -> new VariantPricing(
                        variant.getId(),
                        product.getId(),
                        product.getVendorId(),
                        product.getCategoryId(),
                        categories.findById(product.getCategoryId())
                                .map(Category::getSlug).orElse(null),
                        bestTitle(product),
                        variant.effectivePrice(product.getBasePrice()),
                        product.getCreatorPct(),
                        product.isCodAllowed(),
                        product.isPrepaidOnly(),
                        variant.isActive()
                                && product.getStatus().isPubliclyVisible()
                                && vendors.getVendor(product.getVendorId())
                                        .map(v -> v.canSell()).orElse(false))));
    }

    @Override
    public void reserveStock(String orderId, String variantId, int qty, Duration ttl) {
        stock.reserve(orderId, variantId, qty, ttl);
    }

    @Override
    public void releaseStock(String orderId) {
        stock.release(orderId);
    }

    @Override
    public void consumeStock(String orderId) {
        stock.consume(orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BigDecimal> shippingFee(String district) {
        return shippingFees.findById(district)
                .filter(fee -> fee.isActive())
                .map(fee -> fee.getFee());
    }

    /** FR-LOC-02: fall back rather than showing an empty field. */
    private static String bestTitle(Product product) {
        var title = product.getTitle();
        if (title == null || title.isEmpty()) {
            return product.getSlug();
        }
        for (String language : List.of("en", "si", "ta")) {
            String value = title.get(language);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return title.values().iterator().next();
    }
}
