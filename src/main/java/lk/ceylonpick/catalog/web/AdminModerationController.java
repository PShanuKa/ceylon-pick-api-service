package lk.ceylonpick.catalog.web;

import java.util.Set;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lk.ceylonpick.auth.api.AuthUser;
import lk.ceylonpick.catalog.service.ProductService;
import lk.ceylonpick.shared.web.ApiResponse;
import lk.ceylonpick.shared.web.ClientInfo;
import lk.ceylonpick.shared.web.PageQuery;
import lk.ceylonpick.shared.web.PageResponse;

/**
 * FR-ADM-03 / BR-21: products go live only after an admin approves them.
 *
 * <p>Daily operational work, so both admin tiers can do it.
 */
@RestController
@RequestMapping("/api/v1/admin/products")
public class AdminModerationController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt");

    private final ProductService products;

    public AdminModerationController(ProductService products) {
        this.products = products;
    }

    @GetMapping("/moderation")
    public ApiResponse<PageResponse<CatalogDtos.ProductCard>> queue(PageQuery page) {
        return ApiResponse.ok(PageResponse.of(
                products.moderationQueue(page.toPageable(SORTABLE, "updatedAt")),
                CatalogDtos.ProductCard::of));
    }

    @PostMapping("/{productId}/approve")
    public ApiResponse<CatalogDtos.ProductCard> approve(@AuthenticationPrincipal AuthUser principal,
                                                         @PathVariable String productId,
                                                         HttpServletRequest request) {
        return ApiResponse.ok(CatalogDtos.ProductCard.of(
                products.approve(productId, principal.userId(), ClientInfo.ip(request))));
    }

    /** Sends it back to DRAFT with a note the vendor can act on. */
    @PostMapping("/{productId}/request-changes")
    public ApiResponse<CatalogDtos.ProductCard> requestChanges(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable String productId,
            @Valid @RequestBody CatalogDtos.ModerationNoteRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(CatalogDtos.ProductCard.of(products.requestChanges(
                productId, body.note(), principal.userId(), ClientInfo.ip(request))));
    }
}
