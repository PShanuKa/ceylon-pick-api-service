package lk.ceylonpick.settings.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lk.ceylonpick.settings.api.SettingKeys;
import lk.ceylonpick.settings.api.Settings;
import lk.ceylonpick.settings.domain.Setting;
import lk.ceylonpick.settings.repo.SettingRepository;
import lk.ceylonpick.shared.audit.AuditService;
import lk.ceylonpick.shared.web.ApiException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads and writes the configurable business rules.
 *
 * <p>These are read on nearly every order operation and written a few times a
 * year, which makes them the strongest case for the in-process cache the
 * architecture already prescribes (Caffeine; ADR-2 rules out Redis for v1). The
 * whole table is 17 rows, so it is cached as one map and dropped entirely on any
 * write — cheaper and less error-prone than tracking per-key invalidation.
 */
@Service
public class SettingsService implements Settings {

    private static final String CACHE_KEY = "all";
    public static final String SETTING_CHANGED = "SETTING_CHANGED";

    private final SettingRepository repository;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private final Cache<String, Map<String, String>> cache;

    public SettingsService(SettingRepository repository, ObjectMapper mapper, AuditService audit) {
        this.repository = repository;
        this.mapper = mapper;
        this.audit = audit;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(1)
                .build();
    }

    // ------------------------------------------------------------------ read

    @Override
    public CommissionBounds creatorCommissionBounds() {
        JsonNode node = tree(SettingKeys.CREATOR_COMMISSION_BOUNDS);
        return new CommissionBounds(
                node.path("min").decimalValue(),
                node.path("max").decimalValue());
    }

    @Override
    public VendorCaps vendorCaps() {
        JsonNode node = tree(SettingKeys.VENDOR_CAPS);
        return new VendorCaps(node.path("max_vendors").asInt(), node.path("max_skus_per_vendor").asInt());
    }

    @Override
    public boolean killSwitchOn() {
        return flag(SettingKeys.KILL_SWITCH_NEW_ORDERS);
    }

    @Override
    public BigDecimal decimal(String key) {
        return new BigDecimal(raw(key));
    }

    @Override
    public int integer(String key) {
        return Integer.parseInt(raw(key));
    }

    @Override
    public boolean flag(String key) {
        return Boolean.parseBoolean(raw(key));
    }

    @Override
    public String json(String key) {
        return raw(key);
    }

    @Transactional(readOnly = true)
    public Map<String, String> all() {
        return Map.copyOf(load());
    }

    // ----------------------------------------------------------------- write

    /**
     * FR-ADM-04: a change records actor and time. The value must parse as JSON,
     * because the column is JSONB and a malformed write would only surface later,
     * on the read that needs it.
     */
    @Transactional
    public Setting update(String key, String jsonValue, String actorId, String actorRole,
                          String reason, String ip) {
        Setting setting = repository.findById(key)
                .orElseThrow(() -> ApiException.notFound("UNKNOWN_SETTING", "No such setting: " + key));
        String before = setting.getValue();
        String normalised = normalise(jsonValue);

        setting.setValue(normalised);
        setting.setUpdatedBy(actorId);
        repository.save(setting);
        cache.invalidateAll();

        audit.record(SETTING_CHANGED, actorId, actorRole, "setting", key, ip,
                Map.of("value", before), Map.of("value", normalised), reason);
        return setting;
    }

    private String normalise(String jsonValue) {
        if (jsonValue == null || jsonValue.isBlank()) {
            throw ApiException.badRequest("INVALID_SETTING_VALUE", "A value is required");
        }
        try {
            return mapper.readTree(jsonValue).toString();
        } catch (RuntimeException e) {
            throw ApiException.badRequest("INVALID_SETTING_VALUE", "The value must be valid JSON");
        }
    }

    // --------------------------------------------------------------- private

    private String raw(String key) {
        String value = load().get(key);
        if (value == null) {
            // A missing key means the migration and the code disagree. Failing
            // loudly beats silently applying a default to a money rule.
            throw ApiException.notFound("UNKNOWN_SETTING", "No such setting: " + key);
        }
        return value;
    }

    private JsonNode tree(String key) {
        return mapper.readTree(raw(key));
    }

    private Map<String, String> load() {
        return cache.get(CACHE_KEY, ignored -> {
            List<Setting> rows = repository.findAll();
            Map<String, String> map = new HashMap<>(rows.size());
            for (Setting row : rows) {
                map.put(row.getKey(), row.getValue());
            }
            return map;
        });
    }
}
