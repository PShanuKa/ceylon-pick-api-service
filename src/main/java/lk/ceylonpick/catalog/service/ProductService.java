package lk.ceylonpick.catalog.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.ceylonpick.auth.api.Role;
import lk.ceylonpick.catalog.api.ProductStatus;
import lk.ceylonpick.catalog.domain.Product;
import lk.ceylonpick.catalog.domain.ProductImage;
import lk.ceylonpick.catalog.domain.ProductVariant;
import lk.ceylonpick.catalog.repo.CategoryRepository;
import lk.ceylonpick.catalog.repo.ProductImageRepository;
import lk.ceylonpick.catalog.repo.ProductRepository;
import lk.ceylonpick.catalog.repo.ProductVariantRepository;
import lk.ceylonpick.settings.api.Settings;
import lk.ceylonpick.shared.Ids;
import lk.ceylonpick.shared.audit.AuditService;
import lk.ceylonpick.shared.web.ApiException;

/**
 * Vendor product management and admin moderation.
 *
 * <p>The rule that shapes this class is BR-21: "New or changed products require
 * admin moderation before going live." A vendor can only ever move a product
 * towards moderation; {@link ProductStatus#LIVE} is reachable exclusively
 * through {@link #approve}. Editing something already live sends it back for
 * review rather than publishing the change silently.
 */
@Service
public class ProductService {

    public static final String PRODUCT_SUBMITTED = "PRODUCT_SUBMITTED";
    public static final String PRODUCT_APPROVED = "PRODUCT_APPROVED";
    public static final String PRODUCT_CHANGES_REQUESTED = "PRODUCT_CHANGES_REQUESTED";

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ProductImageRepository images;
    private final CategoryRepository categories;
    private final Settings settings;
    private final AuditService audit;
    private final Clock clock;

    public ProductService(ProductRepository products,
                          ProductVariantRepository variants,
                          ProductImageRepository images,
                          CategoryRepository categories,
                          Settings settings,
                          AuditService audit,
                          Clock clock) {
        this.products = products;
        this.variants = variants;
        this.images = images;
        this.categories = categories;
        this.settings = settings;
        this.audit = audit;
        this.clock = clock;
    }

    public record ProductDraft(
            String categoryId,
            Map<String, String> title,
            Map<String, String> description,
            Map<String, String> ingredients,
            BigDecimal basePrice,
            BigDecimal creatorPct,
            boolean prepaidOnly,
            int leadTimeDays) {
    }

    public record VariantDraft(String sku, Map<String, String> attrs, BigDecimal priceOverride, int stockQty) {
    }

    // ------------------------------------------------------------------ vendor

    @Transactional(readOnly = true)
    public Page<Product> listForVendor(String vendorId, Pageable pageable) {
        return products.findByVendorId(vendorId, pageable);
    }

    /** Loads a product and refuses it if it belongs to another vendor (FR-AUTH-03). */
    @Transactional(readOnly = true)
    public Product requireOwned(String vendorId, String productId) {
        Product product = products.findById(productId)
                .orElseThrow(() -> ApiException.notFound("UNKNOWN_PRODUCT", "No such product"));
        if (!product.getVendorId().equals(vendorId)) {
            throw ApiException.forbidden("NOT_YOUR_PRODUCT", "You do not have access to this");
        }
        return product;
    }

    @Transactional
    public Product create(String vendorId, ProductDraft draft) {
        requireCategory(draft.categoryId());
        requireCommissionInBounds(draft.creatorPct());

        Product product = new Product();
        product.setId(Ids.newId());
        product.setVendorId(vendorId);
        product.setCategoryId(draft.categoryId());
        product.setSlug(uniqueSlug(firstTitle(draft.title())));
        applyDraft(product, draft);
        product.setStatus(ProductStatus.DRAFT);
        product.setCreatedAt(clock.instant());
        return products.save(product);
    }

    @Transactional
    public Product update(String vendorId, String productId, ProductDraft draft) {
        Product product = requireOwned(vendorId, productId);
        requireCategory(draft.categoryId());
        requireCommissionInBounds(draft.creatorPct());

        product.setCategoryId(draft.categoryId());
        applyDraft(product, draft);
        // BR-21 covers changed products, not only new ones.
        if (product.getStatus() == ProductStatus.LIVE) {
            product.setStatus(ProductStatus.IN_MODERATION);
            product.setModerationNote(null);
        }
        return products.save(product);
    }

    /** FR-CAT-02: a product cannot be published with mandatory fields missing. */
    @Transactional
    public Product submitForModeration(String vendorId, String productId, String ip) {
        Product product = requireOwned(vendorId, productId);
        if (product.getTitle() == null || product.getTitle().isEmpty()) {
            throw ApiException.badRequest("PRODUCT_INCOMPLETE", "A title is required");
        }
        if (images.countByProductId(productId) == 0) {
            throw ApiException.badRequest("PRODUCT_INCOMPLETE", "At least one image is required");
        }
        if (variants.findByProductIdOrderBySku(productId).isEmpty()) {
            throw ApiException.badRequest("PRODUCT_INCOMPLETE", "At least one variant is required");
        }
        product.setStatus(ProductStatus.IN_MODERATION);
        products.save(product);
        audit.record(PRODUCT_SUBMITTED, vendorId, Role.VENDOR.name(), "product", productId, ip);
        return product;
    }

    @Transactional
    public Product pause(String vendorId, String productId) {
        Product product = requireOwned(vendorId, productId);
        product.setStatus(ProductStatus.PAUSED);
        return products.save(product);
    }

    /** A pause is not an edit, so resuming does not need re-moderation. */
    @Transactional
    public Product resume(String vendorId, String productId) {
        Product product = requireOwned(vendorId, productId);
        if (product.getStatus() != ProductStatus.PAUSED) {
            throw ApiException.conflict("NOT_PAUSED", "This product is not paused");
        }
        product.setStatus(ProductStatus.LIVE);
        return products.save(product);
    }

    // ----------------------------------------------------------------- variants

    /** BR-20: "max 15 SKUs per vendor", enforced with an explanation (FR-CAT-07). */
    @Transactional
    public ProductVariant addVariant(String vendorId, String productId, VariantDraft draft) {
        requireOwned(vendorId, productId);
        int cap = settings.vendorCaps().maxSkusPerVendor();
        if (variants.countByVendor(vendorId) >= cap) {
            throw ApiException.conflict("SKU_CAP_REACHED",
                    "Phase 1 allows " + cap + " SKUs per maker. Archive one to add another.", cap);
        }
        if (draft.sku() == null || draft.sku().isBlank()) {
            throw ApiException.badRequest("INVALID_SKU", "A SKU is required");
        }
        if (variants.existsBySku(draft.sku())) {
            throw ApiException.conflict("SKU_IN_USE", "That SKU is already in use");
        }
        ProductVariant variant = new ProductVariant();
        variant.setId(Ids.newId());
        variant.setProductId(productId);
        variant.setSku(draft.sku());
        variant.setAttrs(draft.attrs() == null ? Map.of() : draft.attrs());
        variant.setPriceOverride(draft.priceOverride());
        variant.setStockQty(Math.max(0, draft.stockQty()));
        return variants.save(variant);
    }

    /**
     * Inline stock editing from the vendor's product list (FR-VEN-03).
     * Reducing below what is already reserved is refused, not silently clamped.
     */
    @Transactional
    public ProductVariant setStock(String vendorId, String productId, String variantId, int stockQty) {
        requireOwned(vendorId, productId);
        ProductVariant variant = variants.findById(variantId)
                .filter(v -> v.getProductId().equals(productId))
                .orElseThrow(() -> ApiException.notFound("UNKNOWN_VARIANT", "No such variant"));
        if (stockQty < variant.getReservedQty()) {
            throw ApiException.conflict("STOCK_BELOW_RESERVED",
                    "There are " + variant.getReservedQty() + " units already held for open orders");
        }
        variant.setStockQty(stockQty);
        return variants.save(variant);
    }

    @Transactional
    public ProductImage addImage(String vendorId, String productId, String objectKey, String alt, int sortOrder) {
        requireOwned(vendorId, productId);
        ProductImage image = new ProductImage();
        image.setId(Ids.newId());
        image.setProductId(productId);
        image.setObjectKey(objectKey);
        image.setAlt(alt);
        image.setSortOrder(sortOrder);
        return images.save(image);
    }

    // --------------------------------------------------------------- moderation

    @Transactional(readOnly = true)
    public Page<Product> moderationQueue(Pageable pageable) {
        return products.findByStatus(ProductStatus.IN_MODERATION, pageable);
    }

    /** FR-ADM-03. The only path to LIVE. */
    @Transactional
    public Product approve(String productId, String adminId, String ip) {
        Product product = requireProduct(productId);
        if (product.getStatus() != ProductStatus.IN_MODERATION) {
            throw ApiException.conflict("NOT_IN_MODERATION", "This product is not awaiting moderation");
        }
        product.setStatus(ProductStatus.LIVE);
        product.setModerationNote(null);
        products.save(product);
        audit.record(PRODUCT_APPROVED, adminId, Role.ADMIN.name(), "product", productId, ip);
        return product;
    }

    @Transactional
    public Product requestChanges(String productId, String note, String adminId, String ip) {
        Product product = requireProduct(productId);
        if (note == null || note.isBlank()) {
            throw ApiException.badRequest("REASON_REQUIRED", "Say what needs changing");
        }
        product.setStatus(ProductStatus.DRAFT);
        product.setModerationNote(note);
        products.save(product);
        audit.record(PRODUCT_CHANGES_REQUESTED, adminId, Role.ADMIN.name(), "product", productId, ip,
                null, null, note);
        return product;
    }

    // ------------------------------------------------------------------ private

    private Product requireProduct(String productId) {
        return products.findById(productId)
                .orElseThrow(() -> ApiException.notFound("UNKNOWN_PRODUCT", "No such product"));
    }

    private void requireCategory(String categoryId) {
        if (categoryId == null || !categories.existsById(categoryId)) {
            throw ApiException.badRequest("UNKNOWN_CATEGORY", "Choose a category");
        }
    }

    /** BR-02, read from settings so the range can be widened without a deploy. */
    private void requireCommissionInBounds(BigDecimal creatorPct) {
        var bounds = settings.creatorCommissionBounds();
        if (!bounds.contains(creatorPct)) {
            throw ApiException.badRequest("COMMISSION_OUT_OF_BOUNDS",
                    "Creator commission must be between " + bounds.min() + "% and " + bounds.max() + "%",
                    bounds.min(), bounds.max());
        }
    }

    private void applyDraft(Product product, ProductDraft draft) {
        product.setTitle(draft.title());
        product.setDescription(draft.description());
        product.setIngredients(draft.ingredients());
        product.setBasePrice(draft.basePrice());
        product.setCreatorPct(draft.creatorPct());
        product.setLeadTimeDays(Math.max(1, draft.leadTimeDays()));
        // BR-06: setting prepaid-only clears COD, which the database also enforces.
        product.setPrepaidOnly(draft.prepaidOnly());
        if (!draft.prepaidOnly()) {
            product.setCodAllowed(true);
        }
    }

    private static String firstTitle(Map<String, String> title) {
        if (title == null || title.isEmpty()) {
            return "product";
        }
        return title.getOrDefault("en", title.values().iterator().next());
    }

    private String uniqueSlug(String source) {
        String base = java.text.Normalizer.normalize(source == null ? "" : source,
                        java.text.Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isEmpty()) {
            base = "product";
        }
        String candidate = base;
        int suffix = 2;
        while (products.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }
}
