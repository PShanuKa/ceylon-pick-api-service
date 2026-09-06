package lk.ceylonpick.orders.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import lk.ceylonpick.orders.api.AttributionSource;

/**
 * Stands in until the creators module lands: every order is unattributed.
 *
 * <p>Safe as a default because an unattributed order is a real, ordinary case —
 * a buyer who found the shop directly. Nothing downstream special-cases it, and
 * no commission is silently invented.
 *
 * <p>The real implementation should be marked {@code @Primary} rather than this
 * one being made conditional: {@code @ConditionalOnMissingBean} is only reliable
 * inside auto-configuration, and outside it the answer depends on bean
 * definition order.
 */
@Component
public class UnattributedOrders implements AttributionSource {

    @Override
    public Optional<Attribution> resolve(String code, String cookieCreatorId, String buyerPhone) {
        return Optional.empty();
    }
}
