-- =====================================================================
-- CeylonPick V2 — settings, vendors and catalog.
--
-- Taken from doc/version 1/V1__init.sql sections 1, 2, 3 and 10, keeping
-- the column names and types so the orders and ledger migrations line up.
--
-- `setting` arrives here rather than with the admin module because BR-02
-- (creator commission bounds) and BR-20 (vendor and SKU caps) are enforced
-- from the moment vendors can list products, and SRS §3 makes them
-- configurable values with an audit trail, not constants in a jar.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Settings — the configurable half of the business rules
-- ---------------------------------------------------------------------
CREATE TABLE setting (
  key         TEXT PRIMARY KEY,
  value       JSONB NOT NULL,
  description TEXT,
  updated_by  TEXT REFERENCES app_user(id),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO setting (key, value, description) VALUES
 ('platform_commission_pct',      '6',                          'BR-01 platform commission % of product price'),
 ('creator_commission_bounds',    '{"min":10,"max":20}',        'BR-02 vendor-set creator commission bounds %'),
 ('cod_cap_global',               '5000',                       'BR-05 max COD order total LKR'),
 ('cod_cap_by_category',          '{"beauty":3000}',            'BR-05 per-category COD caps LKR'),
 ('otp_rules',                    '{"ttl_min":10,"max_attempts":3,"max_resends":3,"auto_cancel_hours":24,"per_phone_per_hour":3}', 'BR-07 / FR-NOT-01'),
 ('prepaid_timeout_min',          '30',                         'BR-08'),
 ('settlement_hold_days',         '7',                          'BR-10'),
 ('dispute_window_hours',         '48',                         'BR-11'),
 ('creator_payout',               '{"days":[1,16],"min_lkr":2000}', 'BR-14'),
 ('vendor_payout',                '{"weekday":5,"cutoff_weekday":4}', 'BR-15 Friday, cutoff Thursday'),
 ('creator_rto_thresholds',       '{"pause_pct":30,"remove_pct":40,"min_orders":10}', 'BR-16'),
 ('attribution_cookie_days',      '30',                         'BR-17'),
 ('vendor_caps',                  '{"max_vendors":25,"max_skus_per_vendor":15}', 'BR-20'),
 ('rto_fee_lkr',                  '250',                        'BR-22 (to confirm)'),
 ('vendor_strike_limit',          '3',                          'BR-23'),
 ('kill_switch_new_orders',       'false',                      'FR-ORD-14'),
 ('occasion_banner',              '{"active":false,"title":"","href":"","cutoff":null}', 'Home hero banner');

-- ---------------------------------------------------------------------
-- 2. Vendors
--
-- FR-VEN-02: a vendor is VERIFIED only after documents, sample and call are
-- all recorded. Until then their products are never publicly visible, which
-- the catalog queries enforce by joining on vendor.status.
-- ---------------------------------------------------------------------
CREATE TABLE vendor (
  id                  TEXT PRIMARY KEY,
  -- Null until the application is approved and a login is created; the
  -- application itself is made by a visitor with no account (FR-VEN-01).
  user_id             TEXT REFERENCES app_user(id),
  business_name       TEXT NOT NULL,
  maker_name          TEXT NOT NULL,
  slug                TEXT NOT NULL UNIQUE,
  district            TEXT NOT NULL,
  story               TEXT,
  story_video_key     TEXT,
  photo_key           TEXT,
  pickup_address      JSONB,
  contact_phone       TEXT NOT NULL,
  status              TEXT NOT NULL DEFAULT 'APPLIED'
                        CHECK (status IN ('APPLIED','IN_REVIEW','VERIFIED','SUSPENDED','DECLINED')),
  strikes             INT NOT NULL DEFAULT 0,          -- BR-23
  rto_fee_policy      TEXT NOT NULL DEFAULT 'FLAT' CHECK (rto_fee_policy IN ('FLAT','SPLIT','NONE')),
  bank_details_enc    BYTEA,                           -- NFR-06 AES-GCM; key outside the database
  bank_details_masked TEXT,                            -- e.g. 'Commercial ****4417'
  verified_at         TIMESTAMPTZ,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_vendor_user ON vendor (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX ix_vendor_status ON vendor (status);

CREATE TABLE vendor_verification (
  vendor_id             TEXT PRIMARY KEY REFERENCES vendor(id) ON DELETE CASCADE,
  nic_or_br_doc_key     TEXT,
  documents_received_at TIMESTAMPTZ,
  sample_received_at    TIMESTAMPTZ,
  call_completed_at     TIMESTAMPTZ,
  reviewer_id           TEXT REFERENCES app_user(id),
  notes                 TEXT,
  decline_reason        TEXT
);

-- ---------------------------------------------------------------------
-- 3. Catalog
--
-- Names and copy are JSONB keyed by language (FR-LOC-02: content is stored
-- in the vendor's language with optional translations, and the UI shows the
-- best available).
-- ---------------------------------------------------------------------
CREATE TABLE category (
  id         TEXT PRIMARY KEY,
  slug       TEXT NOT NULL UNIQUE,
  name       JSONB NOT NULL,                 -- {"en":"Gifting hampers","si":"...","ta":"..."}
  sort_order INT NOT NULL DEFAULT 0,
  active     BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO category (id, slug, name, sort_order) VALUES
 ('01J0000000000000000000CAT1','gifting',      '{"en":"Gifting & hampers"}', 1),
 ('01J0000000000000000000CAT2','personalised', '{"en":"Personalised gifts"}', 2),
 ('01J0000000000000000000CAT3','beauty',       '{"en":"Natural beauty & wellness"}', 3);

CREATE TABLE product (
  id              TEXT PRIMARY KEY,
  vendor_id       TEXT NOT NULL REFERENCES vendor(id),
  category_id     TEXT NOT NULL REFERENCES category(id),
  slug            TEXT NOT NULL UNIQUE,
  title           JSONB NOT NULL,
  description     JSONB,
  ingredients     JSONB,
  base_price      NUMERIC(12,2) NOT NULL CHECK (base_price > 0),
  -- BR-02 bounds (10-20%) are a setting, so they are enforced in the service
  -- rather than frozen into a constraint; this is the absolute sanity bound.
  creator_pct     NUMERIC(5,2) NOT NULL CHECK (creator_pct BETWEEN 0 AND 50),
  cod_allowed     BOOLEAN NOT NULL DEFAULT TRUE,
  prepaid_only    BOOLEAN NOT NULL DEFAULT FALSE,      -- BR-06
  lead_time_days  INT NOT NULL DEFAULT 1,
  status          TEXT NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT','IN_MODERATION','LIVE','PAUSED','ARCHIVED')),
  moderation_note TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (NOT (prepaid_only AND cod_allowed))
);
CREATE INDEX ix_product_live ON product (category_id, status) WHERE status = 'LIVE';
CREATE INDEX ix_product_vendor ON product (vendor_id);

CREATE TABLE product_image (
  id         TEXT PRIMARY KEY,
  product_id TEXT NOT NULL REFERENCES product(id) ON DELETE CASCADE,
  object_key TEXT NOT NULL,                            -- R2 key; the bytes never touch this service
  sort_order INT NOT NULL DEFAULT 0,
  alt        TEXT
);
CREATE INDEX ix_product_image_product ON product_image (product_id, sort_order);

CREATE TABLE product_variant (
  id             TEXT PRIMARY KEY,
  product_id     TEXT NOT NULL REFERENCES product(id) ON DELETE CASCADE,
  sku            TEXT NOT NULL UNIQUE,
  attrs          JSONB NOT NULL DEFAULT '{}',          -- {"size":"250g"}
  price_override NUMERIC(12,2),
  stock_qty      INT NOT NULL DEFAULT 0 CHECK (stock_qty >= 0),
  reserved_qty   INT NOT NULL DEFAULT 0 CHECK (reserved_qty >= 0),
  active         BOOLEAN NOT NULL DEFAULT TRUE,
  -- The invariant that makes overselling impossible even under concurrent
  -- checkouts: the database refuses the second reservation of a last unit.
  CHECK (reserved_qty <= stock_qty)
);
CREATE INDEX ix_variant_product ON product_variant (product_id);

-- FR-CAT-04: 15 minutes for prepaid, 24 hours for COD, released on
-- cancellation or timeout.
CREATE TABLE stock_reservation (
  id          TEXT PRIMARY KEY,
  variant_id  TEXT NOT NULL REFERENCES product_variant(id),
  -- The FK to "order"(id) is added by the orders migration; that table does
  -- not exist yet.
  order_id    TEXT NOT NULL,
  qty         INT NOT NULL CHECK (qty > 0),
  expires_at  TIMESTAMPTZ NOT NULL,
  released_at TIMESTAMPTZ,
  consumed_at TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_reservation_due ON stock_reservation (expires_at)
  WHERE released_at IS NULL AND consumed_at IS NULL;
CREATE INDEX ix_reservation_order ON stock_reservation (order_id);

-- ---------------------------------------------------------------------
-- 4. Shipping fee by district (BR-04)
--
-- Catalog needs it because FR-CAT-03 puts the delivery estimate on the
-- product page, before the buy action.
-- ---------------------------------------------------------------------
CREATE TABLE shipping_fee (
  district TEXT PRIMARY KEY,
  fee      NUMERIC(12,2) NOT NULL,
  active   BOOLEAN NOT NULL DEFAULT TRUE,
  eta_days INT NOT NULL DEFAULT 2
);
-- Project Plan §4: Colombo, Gampaha and Kalutara for months 1-3.
INSERT INTO shipping_fee (district, fee, eta_days) VALUES
 ('Colombo', 300, 1), ('Gampaha', 350, 1), ('Kalutara', 350, 2);

-- ---------------------------------------------------------------------
-- 5. updated_at maintenance (touch_updated_at() comes from V1)
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_touch_vendor  BEFORE UPDATE ON vendor  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER trg_touch_product BEFORE UPDATE ON product FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER trg_touch_setting BEFORE UPDATE ON setting FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
