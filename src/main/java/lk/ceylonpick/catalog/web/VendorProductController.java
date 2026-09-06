package lk.ceylonpick.catalog.web;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lk.ceylonpick.auth.api.AuthUser;
import lk.ceylonpick.catalog.domain.Product;
import lk.ceylonpick.catalog.service.CatalogService;
import lk.ceylonpick.catalog.service.ProductService;
import lk.ceylonpick.shared.web.ApiResponse;
import lk.ceylonpick.shared.web.ClientInfo;
import lk.ceylonpick.shared.web.PageQuery;
import lk.ceylonpick.shared.web.PageResponse;
import lk.ceylonpick.vendors.api.VendorApi;

/**
 * A vendor managing their own products (FR-VEN-03).
 *
 * <p>The vendor id is never a path parameter: it is resolved from the signed-in
 * user on every call, so vendor A has no way to name vendor B (FR-AUTH-03).
 */
@RestController
@RequestMapping("/api/v1/vendor/products")
@PreAuthorize("hasRole('VENDOR')")
public class VendorProductController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "basePrice", "status");

    private final ProductService productService;
    private final CatalogService catalog;
    private final VendorApi vendors;

    public VendorProductController(ProductService productService, CatalogService catalog, VendorApi vendors) {
        this.productService = productService;
        this.catalog = catalog;
        this.vendors = vendors;
    }

    private String vendorId(AuthUser principal) {
        return vendors.requireOwnVendor(principal.userId()).id();
    }

    @GetMapping
    public ApiResponse<PageResponse<CatalogDtos.ProductCard>> list(
            @AuthenticationPrincipal AuthUser principal, PageQuery page) {
        return ApiResponse.ok(PageResponse.of(
                productService.listForVendor(vendorId(principal), page.toPageable(SORTABLE, "createdAt")),
                CatalogDtos.ProductCard::of));
    }

    @GetMapping("/{productId}")
    public ApiResponse<CatalogDtos.ProductDetail> detail(@AuthenticationPrincipal AuthUser principal,
                                                          @PathVariable String productId) {
        Product product = productService.requireOwned(vendorId(principal), productId);
        return ApiResponse.ok(CatalogDtos.ProductDetail.of(product,
                catalog.variantsOf(productId), catalog.imagesOf(productId), null, true));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CatalogDtos.ProductCard>> create(
            @AuthenticationPrincipal AuthUser principal,
            @Valid @RequestBody CatalogDtos.ProductRequest body) {
        Product product = productService.create(vendorId(principal), toDraft(body));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(CatalogDtos.ProductCard.of(product)));
    }

    /** Editing a LIVE product sends it back to moderation (BR-21). */
    @PutMapping("/{productId}")
    public ApiResponse<CatalogDtos.ProductCard> update(@AuthenticationPrincipal AuthUser principal,
                                                        @PathVariable String productId,
                                                        @Valid @RequestBody CatalogDtos.ProductRequest body) {
        return ApiResponse.ok(CatalogDtos.ProductCard.of(
                productService.update(vendorId(principal), productId, toDraft(body))));
    }

    @PostMapping("/{productId}/submit")
    public ApiResponse<CatalogDtos.ProductCard> submit(@AuthenticationPrincipal AuthUser principal,
                                                        @PathVariable String productId,
                                                        HttpServletRequest request) {
        return ApiResponse.ok(CatalogDtos.ProductCard.of(productService.submitForModeration(
                vendorId(principal), productId, ClientInfo.ip(request))));
    }

    @PostMapping("/{productId}/pause")
    public ApiResponse<CatalogDtos.ProductCard> pause(@AuthenticationPrincipal AuthUser principal,
                                                       @PathVariable String productId) {
        return ApiResponse.ok(CatalogDtos.ProductCard.of(
                productService.pause(vendorId(principal), productId)));
    }

    @PostMapping("/{productId}/resume")
    public ApiResponse<CatalogDtos.ProductCard> resume(@AuthenticationPrincipal AuthUser principal,
                                                        @PathVariable String productId) {
        return ApiResponse.ok(CatalogDtos.ProductCard.of(
                productService.resume(vendorId(principal), productId)));
    }

    @PostMapping("/{productId}/variants")
    public ResponseEntity<ApiResponse<CatalogDtos.VariantView>> addVariant(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable String productId,
            @Valid @RequestBody CatalogDtos.VariantRequest body) {
        var variant = productService.addVariant(vendorId(principal), productId,
                new ProductService.VariantDraft(body.sku(), body.attrs(), body.priceOverride(), body.stockQty()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(CatalogDtos.VariantView.of(variant)));
    }

    /** FR-VEN-03: "stock edit is inline". */
    @PutMapping("/{productId}/variants/{variantId}/stock")
    public ApiResponse<CatalogDtos.VariantView> setStock(@AuthenticationPrincipal AuthUser principal,
                                                          @PathVariable String productId,
                                                          @PathVariable String variantId,
                                                          @Valid @RequestBody CatalogDtos.StockRequest body) {
        return ApiResponse.ok(CatalogDtos.VariantView.of(
                productService.setStock(vendorId(principal), productId, variantId, body.stockQty())));
    }

    /** The browser uploads to R2 directly; only the resulting key arrives here. */
    @PostMapping("/{productId}/images")
    public ResponseEntity<ApiResponse<CatalogDtos.ImageView>> addImage(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable String productId,
            @Valid @RequestBody CatalogDtos.ImageRequest body) {
        var image = productService.addImage(vendorId(principal), productId,
                body.objectKey(), body.alt(), body.sortOrder());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(CatalogDtos.ImageView.of(image)));
    }

    private static ProductService.ProductDraft toDraft(CatalogDtos.ProductRequest body) {
        return new ProductService.ProductDraft(body.categoryId(), body.title(), body.description(),
                body.ingredients(), body.basePrice(), body.creatorPct(), body.prepaidOnly(),
                body.leadTimeDays());
    }
}
