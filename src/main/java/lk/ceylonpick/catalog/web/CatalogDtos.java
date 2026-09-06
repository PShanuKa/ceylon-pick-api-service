package lk.ceylonpick.catalog.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lk.ceylonpick.catalog.api.ProductStatus;
import lk.ceylonpick.catalog.domain.Category;
import lk.ceylonpick.catalog.domain.Product;
import lk.ceylonpick.catalog.domain.ProductImage;
import lk.ceylonpick.catalog.domain.ProductVariant;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    // ------------------------------------------------------------- requests

    public record ProductRequest(
            @NotBlank String categoryId,
            @NotEmpty Map<String, String> title,
            Map<String, String> description,
            Map<String, String> ingredients,
            @NotNull @DecimalMin(value = "0.01") BigDecimal basePrice,
            /** BR-02; the accepted range comes from settings, not from here. */
            @NotNull BigDecimal creatorPct,
            boolean prepaidOnly,
            @Min(1) int leadTimeDays) {
    }

    public record VariantRequest(
            @NotBlank String sku,
            Map<String, String> attrs,
            BigDecimal priceOverride,
            @Min(0) int stockQty) {
    }

    public record StockRequest(@Min(0) int stockQty) {
    }

    public record ImageRequest(@NotBlank String objectKey, String alt, int sortOrder) {
    }

    public record ModerationNoteRequest(@NotBlank String note) {
    }

    // ------------------------------------------------------------ responses

    public record CategoryView(String id, String slug, Map<String, String> name, int sortOrder) {

        public static CategoryView of(Category category) {
            return new CategoryView(category.getId(), category.getSlug(), category.getName(),
                    category.getSortOrder());
        }
    }

    /** The card on a category or search page (FR-CAT-01). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProductCard(
            String id,
            String slug,
            Map<String, String> title,
            BigDecimal basePrice,
            String categoryId,
            String vendorId,
            boolean codAllowed,
            boolean prepaidOnly,
            int leadTimeDays) {

        public static ProductCard of(Product product) {
            return new ProductCard(product.getId(), product.getSlug(), product.getTitle(),
                    product.getBasePrice(), product.getCategoryId(), product.getVendorId(),
                    product.isCodAllowed(), product.isPrepaidOnly(), product.getLeadTimeDays());
        }
    }

    public record VariantView(
            String id,
            String sku,
            Map<String, String> attrs,
            BigDecimal priceOverride,
            int stockQty,
            int availableQty,
            boolean active) {

        public static VariantView of(ProductVariant variant) {
            return new VariantView(variant.getId(), variant.getSku(), variant.getAttrs(),
                    variant.getPriceOverride(), variant.getStockQty(), variant.availableQty(),
                    variant.isActive());
        }
    }

    public record ImageView(String id, String objectKey, String alt, int sortOrder) {

        public static ImageView of(ProductImage image) {
            return new ImageView(image.getId(), image.getObjectKey(), image.getAlt(), image.getSortOrder());
        }
    }

    /** The full product page, including the FR-CAT-03 delivery estimate. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProductDetail(
            String id,
            String slug,
            Map<String, String> title,
            Map<String, String> description,
            Map<String, String> ingredients,
            BigDecimal basePrice,
            BigDecimal creatorPct,
            boolean codAllowed,
            boolean prepaidOnly,
            int leadTimeDays,
            ProductStatus status,
            String moderationNote,
            String vendorId,
            List<VariantView> variants,
            List<ImageView> images,
            BigDecimal deliveryFeeFrom) {

        public static ProductDetail of(Product p, List<ProductVariant> variants,
                                       List<ProductImage> images, BigDecimal deliveryFeeFrom,
                                       boolean includeModeration) {
            return new ProductDetail(p.getId(), p.getSlug(), p.getTitle(), p.getDescription(),
                    p.getIngredients(), p.getBasePrice(), p.getCreatorPct(), p.isCodAllowed(),
                    p.isPrepaidOnly(), p.getLeadTimeDays(),
                    includeModeration ? p.getStatus() : null,
                    includeModeration ? p.getModerationNote() : null,
                    p.getVendorId(),
                    variants.stream().map(VariantView::of).toList(),
                    images.stream().map(ImageView::of).toList(),
                    deliveryFeeFrom);
        }
    }
}
