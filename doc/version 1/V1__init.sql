-- =====================================================================
-- CeylonPick — Flyway V1: initial schema
-- Source of truth: Technical Architecture v1.0 §5 (Domain), §6 (State machine), §7 (Ledger), SRS v1.0 §3 (Business rules)
-- Conventions: ULID text ids, LKR NUMERIC(12,2), TIMESTAMPTZ (UTC), snake_case.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;

-- ---------------------------------------------------------------------
-- 0. Auth & users (creators, vendors, admins). Buyers are phone-only.
-- ---------------------------------------------------------------------
CREATE TABLE app_user (
  id            TEXT PRIMARY KEY,
  email         CITEXT,
  phone         TEXT,
  password_hash TEXT,
  role          TEXT NOT NULL CHECK (role IN ('ADMIN','CREATOR','VENDOR','BUYER')),
  status        TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','LOCKED','DISABLED')),
  failed_logins INT  NOT NULL DEFAULT 0,
  locked_until  TIMESTAMPTZ,
  language      CHAR(2) NOT NULL DEFAULT 'en' CHECK (language IN ('en','si','ta')),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (email),
  CHECK (role = 'BUYER' OR email IS NOT NULL)
);
CREATE UNIQUE INDEX ux_app_user_phone_buyer ON app_user (phone) WHERE role = 'BUYER';

CREATE TABLE terms_acceptance (
  id          TEXT PRIMARY KEY,
  user_id     TEXT NOT NULL REFERENCES app_user(id),
  terms_type  TEXT NOT NULL CHECK (terms_type IN ('CREATOR','VENDOR','BUYER_PRIVACY')),
  version     TEXT NOT NULL,
  accepted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ip_hash     TEXT
);

-- ---------------------------------------------------------------------
-- 1. Settings & audit (BR-* configurable values live here)
-- ---------------------------------------------------------------------
CREATE TABLE setting (
  key         TEXT PRIMARY KEY,
  value       JSONB NOT NULL,
  description TEXT,
  updated_by  TEXT,
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

CREATE TABLE audit_log (
  id          BIGSERIAL PRIMARY KEY,
  actor_id    TEXT,
  actor_role  TEXT,
  action      TEXT NOT NULL,          -- e.g. SETTING_CHANGED, ORDER_OVERRIDE, VENDOR_VERIFIED
  entity_type TEXT,
  entity_id   TEXT,
  before      JSONB,
  after       JSONB,
  reason      TEXT,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_entity ON audit_log (entity_type, entity_id, created_at DESC);

-- ---------------------------------------------------------------------
-- 2. Vendors
-- ---------------------------------------------------------------------
CREATE TABLE vendor (
  id               TEXT PRIMARY KEY,
  user_id          TEXT REFERENCES app_user(id),
  business_name    TEXT NOT NULL,
  maker_name       TEXT NOT NULL,
  slug             TEXT NOT NULL UNIQUE,
  district         TEXT NOT NULL,
  story            TEXT,
  story_video_key  TEXT,
  photo_key        TEXT,
  pickup_address   JSONB,
  contact_phone    TEXT NOT NULL,
  status           TEXT NOT NULL DEFAULT 'APPLIED' CHECK (status IN ('APPLIED','IN_REVIEW','VERIFIED','SUSPENDED','DECLINED')),
  strikes          INT NOT NULL DEFAULT 0,
  rto_fee_policy   TEXT NOT NULL DEFAULT 'FLAT' CHECK (rto_fee_policy IN ('FLAT','SPLIT','NONE')),
  bank_details_enc BYTEA,                        -- AES-GCM ciphertext; key outside DB
  bank_details_masked TEXT,                      -- e.g. 'Commercial ****4417'
  verified_at      TIMESTAMPTZ,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE vendor_verification (
  vendor_id          TEXT PRIMARY KEY REFERENCES vendor(id),
  nic_or_br_doc_key  TEXT,
  documents_received_at TIMESTAMPTZ,
  sample_received_at    TIMESTAMPTZ,
  call_completed_at     TIMESTAMPTZ,
  reviewer_id           TEXT,
  notes                 TEXT,
  decline_reason        TEXT
);

-- ---------------------------------------------------------------------
-- 3. Catalog
-- ---------------------------------------------------------------------
CREATE TABLE category (
  id          TEXT PRIMARY KEY,
  slug        TEXT NOT NULL UNIQUE,
  name        JSONB NOT NULL,          -- {"en":"Gifting hampers","si":"...","ta":"..."}
  sort_order  INT NOT NULL DEFAULT 0,
  active      BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO category (id, slug, name, sort_order) VALUES
 ('01J0000000000000000000CAT1','gifting',      '{"en":"Gifting & hampers"}', 1),
 ('01J0000000000000000000CAT2','personalised', '{"en":"Personalised gifts"}', 2),
 ('01J0000000000000000000CAT3','beauty',       '{"en":"Natural beauty & wellness"}', 3);

CREATE TABLE product (
  id             TEXT PRIMARY KEY,
  vendor_id      TEXT NOT NULL REFERENCES vendor(id),
  category_id    TEXT NOT NULL REFERENCES category(id),
  slug           TEXT NOT NULL UNIQUE,
  title          JSONB NOT NULL,        -- per-language
  description    JSONB,
  ingredients    JSONB,
  base_price     NUMERIC(12,2) NOT NULL CHECK (base_price > 0),
  creator_pct    NUMERIC(5,2)  NOT NULL CHECK (creator_pct BETWEEN 0 AND 50),   -- bounds enforced by service (BR-02)
  cod_allowed    BOOLEAN NOT NULL DEFAULT TRUE,
  prepaid_only   BOOLEAN NOT NULL DEFAULT FALSE,   -- BR-06
  lead_time_days INT NOT NULL DEFAULT 1,
  status         TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','IN_MODERATION','LIVE','PAUSED','ARCHIVED')),
  moderation_note TEXT,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (NOT (prepaid_only AND cod_allowed))
);
CREATE INDEX ix_product_live ON product (category_id, status) WHERE status = 'LIVE';
CREATE INDEX ix_product_vendor ON product (vendor_id);

CREATE TABLE product_image (
  id          TEXT PRIMARY KEY,
  product_id  TEXT NOT NULL REFERENCES product(id) ON DELETE CASCADE,
  object_key  TEXT NOT NULL,
  sort_order  INT NOT NULL DEFAULT 0,
  alt         TEXT
);

CREATE TABLE product_variant (
  id             TEXT PRIMARY KEY,
  product_id     TEXT NOT NULL REFERENCES product(id) ON DELETE CASCADE,
  sku            TEXT NOT NULL UNIQUE,
  attrs          JSONB NOT NULL DEFAULT '{}',   -- {"size":"250g"}
  price_override NUMERIC(12,2),
  stock_qty      INT NOT NULL DEFAULT 0 CHECK (stock_qty >= 0),
  reserved_qty   INT NOT NULL DEFAULT 0 CHECK (reserved_qty >= 0),
  active         BOOLEAN NOT NULL DEFAULT TRUE,
  CHECK (reserved_qty <= stock_qty)
);

CREATE TABLE stock_reservation (
  id          TEXT PRIMARY KEY,
  variant_id  TEXT NOT NULL REFERENCES product_variant(id),
  order_id    TEXT NOT NULL,
  qty         INT NOT NULL CHECK (qty > 0),
  expires_at  TIMESTAMPTZ NOT NULL,
  released_at TIMESTAMPTZ,
  consumed_at TIMESTAMPTZ
);
CREATE INDEX ix_reservation_due ON stock_reservation (expires_at) WHERE released_at IS NULL AND consumed_at IS NULL;

-- ---------------------------------------------------------------------
-- 4. Creators & attribution
-- ---------------------------------------------------------------------
CREATE TABLE creator (
  id              TEXT PRIMARY KEY,
  user_id         TEXT REFERENCES app_user(id),
  handle          CITEXT NOT NULL UNIQUE,
  display_name    TEXT NOT NULL,
  code            TEXT NOT NULL UNIQUE,                -- e.g. NIMALI10
  phone           TEXT NOT NULL,                       -- used for BR-18 self-referral block
  bio             TEXT,
  avatar_key      TEXT,
  cover_key       TEXT,
  socials         JSONB NOT NULL DEFAULT '{}',
  follower_range  TEXT,
  niche           TEXT,
  languages       TEXT[] NOT NULL DEFAULT '{en}',
  tier            TEXT NOT NULL DEFAULT 'GROWTH' CHECK (tier IN ('FOUNDING','GROWTH','NANO')),
  status          TEXT NOT NULL DEFAULT 'APPLIED' CHECK (status IN ('APPLIED','APPROVED','PAUSED','REMOVED','DECLINED')),
  pause_reason    TEXT,
  rto_rate_30d    NUMERIC(5,2),
  orders_30d      INT NOT NULL DEFAULT 0,
  bank_details_enc BYTEA,
  bank_details_masked TEXT,
  approved_at     TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE creator_pick (
  creator_id  TEXT NOT NULL REFERENCES creator(id) ON DELETE CASCADE,
  product_id  TEXT NOT NULL REFERENCES product(id),
  note        VARCHAR(80),
  sort_order  INT NOT NULL DEFAULT 0,
  PRIMARY KEY (creator_id, product_id)
);

CREATE TABLE attribution_link (
  id          TEXT PRIMARY KEY,
  creator_id  TEXT NOT NULL REFERENCES creator(id),
  product_id  TEXT REFERENCES product(id),            -- NULL = storefront link
  code        TEXT NOT NULL,                          -- creator code (denormalised)
  utm_tag     TEXT,
  click_count BIGINT NOT NULL DEFAULT 0,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_link_creator ON attribution_link (creator_id);

CREATE TABLE click (
  id          BIGSERIAL PRIMARY KEY,
  link_id     TEXT REFERENCES attribution_link(id),
  creator_id  TEXT NOT NULL REFERENCES creator(id),
  session_id  TEXT,
  ip_hash     TEXT,
  user_agent  TEXT,
  ts          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_click_ts ON click (ts);   -- purge > 90 days (NFR-06)

-- ---------------------------------------------------------------------
-- 5. Orders
-- ---------------------------------------------------------------------
CREATE TABLE "order" (
  id               TEXT PRIMARY KEY,
  number           TEXT NOT NULL UNIQUE,              -- CP-10231
  vendor_id        TEXT NOT NULL REFERENCES vendor(id),   -- BR-19 single vendor per order
  buyer_user_id    TEXT REFERENCES app_user(id),
  buyer_phone      TEXT NOT NULL,
  buyer_name       TEXT NOT NULL,
  address          JSONB NOT NULL,                    -- {line, district, city, landmark}
  district         TEXT NOT NULL,
  language         CHAR(2) NOT NULL DEFAULT 'en',
  pay_method       TEXT NOT NULL CHECK (pay_method IN ('COD','PREPAID')),
  status           TEXT NOT NULL CHECK (status IN (
                     'PLACED','AWAITING_PAYMENT','AWAITING_OTP','CONFIRMED','PACKED','SHIPPED',
                     'DELIVERED','SETTLED','DISPUTED','RTO','CANCELLED','REFUNDED')),
  subtotal         NUMERIC(12,2) NOT NULL,
  shipping_fee     NUMERIC(12,2) NOT NULL DEFAULT 0,
  total            NUMERIC(12,2) NOT NULL,
  creator_id       TEXT REFERENCES creator(id),       -- frozen at PLACED (BR-17)
  attribution_type TEXT CHECK (attribution_type IN ('LINK','CODE')),
  self_referral_flag BOOLEAN NOT NULL DEFAULT FALSE,  -- BR-18
  packing_photo_key TEXT,
  cancel_reason    TEXT,
  version          INT NOT NULL DEFAULT 0,            -- optimistic lock
  placed_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  confirmed_at     TIMESTAMPTZ,
  packed_at        TIMESTAMPTZ,
  shipped_at       TIMESTAMPTZ,
  delivered_at     TIMESTAMPTZ,
  settled_at       TIMESTAMPTZ,
  closed_at        TIMESTAMPTZ,
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (total = subtotal + shipping_fee)
);
CREATE INDEX ix_order_status ON "order" (status, updated_at);
CREATE INDEX ix_order_vendor ON "order" (vendor_id, status);
CREATE INDEX ix_order_creator ON "order" (creator_id, placed_at) WHERE creator_id IS NOT NULL;
CREATE INDEX ix_order_buyer_phone ON "order" (buyer_phone, placed_at DESC);
CREATE INDEX ix_order_settle_due ON "order" (delivered_at) WHERE status = 'DELIVERED';

CREATE SEQUENCE order_number_seq START 10001;

CREATE TABLE order_item (
  id           TEXT PRIMARY KEY,
  order_id     TEXT NOT NULL REFERENCES "order"(id) ON DELETE CASCADE,
  variant_id   TEXT NOT NULL REFERENCES product_variant(id),
  product_id   TEXT NOT NULL REFERENCES product(id),
  vendor_id    TEXT NOT NULL REFERENCES vendor(id),
  title_snapshot TEXT NOT NULL,
  qty          INT NOT NULL CHECK (qty > 0),
  unit_price   NUMERIC(12,2) NOT NULL,               -- snapshot
  creator_pct  NUMERIC(5,2) NOT NULL,                -- snapshot (BR-02)
  platform_pct NUMERIC(5,2) NOT NULL,                -- snapshot (BR-01)
  line_total   NUMERIC(12,2) GENERATED ALWAYS AS (qty * unit_price) STORED
);
CREATE INDEX ix_item_order ON order_item (order_id);

CREATE TABLE order_status_history (
  id          BIGSERIAL PRIMARY KEY,
  order_id    TEXT NOT NULL REFERENCES "order"(id) ON DELETE CASCADE,
  from_status TEXT,
  to_status   TEXT NOT NULL,
  actor_type  TEXT NOT NULL CHECK (actor_type IN ('BUYER','VENDOR','ADMIN','SYSTEM','COURIER','GATEWAY')),
  actor_id    TEXT,
  reason      TEXT,
  meta        JSONB,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_osh_order ON order_status_history (order_id, id);

CREATE TABLE otp_challenge (
  id           TEXT PRIMARY KEY,
  purpose      TEXT NOT NULL CHECK (purpose IN ('ORDER_CONFIRM','BUYER_LOGIN','ADMIN_LOGIN','BANK_EDIT')),
  order_id     TEXT REFERENCES "order"(id) ON DELETE CASCADE,
  user_id      TEXT REFERENCES app_user(id),
  phone        TEXT NOT NULL,
  code_hash    TEXT NOT NULL,                        -- sha256(salt || code)
  salt         TEXT NOT NULL,
  channel      TEXT NOT NULL CHECK (channel IN ('SMS','WHATSAPP')),
  attempts     INT NOT NULL DEFAULT 0,
  resends      INT NOT NULL DEFAULT 0,
  expires_at   TIMESTAMPTZ NOT NULL,
  confirmed_at TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_otp_phone_recent ON otp_challenge (phone, created_at DESC);

CREATE TABLE shipment (
  id             TEXT PRIMARY KEY,
  order_id       TEXT NOT NULL UNIQUE REFERENCES "order"(id) ON DELETE CASCADE,
  courier        TEXT NOT NULL,                      -- KOOMBIYO | PROMPT_XPRESS | PRONTO | MANUAL
  tracking_no    TEXT,
  status         TEXT NOT NULL DEFAULT 'CREATED' CHECK (status IN ('CREATED','IN_TRANSIT','FAILED_ATTEMPT','DELIVERED','RETURNED')),
  failed_attempts INT NOT NULL DEFAULT 0,
  cod_amount     NUMERIC(12,2) NOT NULL DEFAULT 0,
  remitted_at    TIMESTAMPTZ,
  remittance_ref TEXT,
  return_fee     NUMERIC(12,2),
  raw_status     TEXT,
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_shipment_tracking ON shipment (courier, tracking_no);
CREATE INDEX ix_shipment_unremitted ON shipment (courier) WHERE status = 'DELIVERED' AND remitted_at IS NULL;

CREATE TABLE dispute (
  id           TEXT PRIMARY KEY,
  order_id     TEXT NOT NULL UNIQUE REFERENCES "order"(id),
  reason       TEXT NOT NULL CHECK (reason IN ('DAMAGED','WRONG_ITEM','MISSING_ITEM','OTHER')),
  note         VARCHAR(200),
  photo_keys   TEXT[] NOT NULL,
  status       TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','REJECTED','REFUNDED','RETURNED')),
  decision_reason TEXT,
  decided_by   TEXT,
  opened_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  decided_at   TIMESTAMPTZ
);

-- ---------------------------------------------------------------------
-- 6. Payments (PayHere)
-- ---------------------------------------------------------------------
CREATE TABLE payment (
  id            TEXT PRIMARY KEY,
  order_id      TEXT NOT NULL REFERENCES "order"(id),
  provider      TEXT NOT NULL DEFAULT 'PAYHERE',
  provider_ref  TEXT,                                -- payhere payment_id (idempotency key, FR-PAY-02)
  amount        NUMERIC(12,2) NOT NULL,
  currency      CHAR(3) NOT NULL DEFAULT 'LKR',
  status        TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SUCCEEDED','FAILED','CANCELLED','REFUNDED')),
  status_code   TEXT,
  ipn_payload   JSONB,
  fee_amount    NUMERIC(12,2),
  refund_ref    TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_payment_provider_ref ON payment (provider, provider_ref) WHERE provider_ref IS NOT NULL;
CREATE INDEX ix_payment_order ON payment (order_id);

CREATE TABLE ipn_log (
  id          BIGSERIAL PRIMARY KEY,
  provider    TEXT NOT NULL,
  payload     JSONB NOT NULL,
  signature_ok BOOLEAN,
  processed   BOOLEAN NOT NULL DEFAULT FALSE,
  error       TEXT,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 7. Ledger (double-entry, append-only) — Architecture §7
-- ---------------------------------------------------------------------
CREATE TABLE ledger_account (
  id         TEXT PRIMARY KEY,
  type       TEXT NOT NULL CHECK (type IN ('BANK','HELD_FUNDS','COURIER_CLEARING','GATEWAY_CLEARING','GATEWAY_FEE',
                                          'VENDOR_PAYABLE','CREATOR_PAYABLE','PLATFORM_REVENUE','RTO_FEES','REFUNDS')),
  owner_type TEXT NOT NULL CHECK (owner_type IN ('PLATFORM','VENDOR','CREATOR','COURIER','GATEWAY')),
  owner_id   TEXT,
  currency   CHAR(3) NOT NULL DEFAULT 'LKR',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (type, owner_type, owner_id)
);
INSERT INTO ledger_account (id, type, owner_type) VALUES
 ('ACC_BANK',            'BANK',             'PLATFORM'),
 ('ACC_HELD',            'HELD_FUNDS',       'PLATFORM'),
 ('ACC_GATEWAY_FEE',     'GATEWAY_FEE',      'PLATFORM'),
 ('ACC_PLATFORM_REV',    'PLATFORM_REVENUE', 'PLATFORM'),
 ('ACC_RTO_FEES',        'RTO_FEES',         'PLATFORM'),
 ('ACC_REFUNDS',         'REFUNDS',          'PLATFORM');

CREATE TABLE ledger_txn (
  id          TEXT PRIMARY KEY,
  kind        TEXT NOT NULL CHECK (kind IN ('COD_DELIVERED','PREPAID_CAPTURED','REMITTANCE','GATEWAY_SETTLEMENT',
                                           'SETTLEMENT','PAYOUT','REFUND','RTO_FEE','ADJUSTMENT')),
  order_id    TEXT REFERENCES "order"(id),
  payout_id   TEXT,
  idem_key    TEXT UNIQUE,                            -- e.g. 'SETTLEMENT:<order_id>'
  memo        TEXT,
  created_by  TEXT NOT NULL,                          -- SYSTEM | ADMIN:<id>
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_txn_order ON ledger_txn (order_id);
CREATE INDEX ix_txn_kind_time ON ledger_txn (kind, created_at);

CREATE TABLE ledger_entry (
  id          BIGSERIAL PRIMARY KEY,
  txn_id      TEXT NOT NULL REFERENCES ledger_txn(id),
  account_id  TEXT NOT NULL REFERENCES ledger_account(id),
  debit       NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (debit  >= 0),
  credit      NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (credit >= 0),
  CHECK ((debit = 0) <> (credit = 0))
);
CREATE INDEX ix_entry_account ON ledger_entry (account_id, id);
CREATE INDEX ix_entry_txn ON ledger_entry (txn_id);

-- Balanced-transaction guard, checked at COMMIT (deferred).
CREATE OR REPLACE FUNCTION ledger_txn_balanced() RETURNS TRIGGER AS $$
DECLARE diff NUMERIC(14,2);
BEGIN
  SELECT COALESCE(SUM(debit) - SUM(credit), 0) INTO diff FROM ledger_entry WHERE txn_id = NEW.txn_id;
  IF diff <> 0 THEN
    RAISE EXCEPTION 'ledger_txn % is unbalanced by %', NEW.txn_id, diff;
  END IF;
  RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_ledger_balanced
  AFTER INSERT ON ledger_entry
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION ledger_txn_balanced();

-- Append-only guard (BR-24). App role must also lack UPDATE/DELETE grants.
CREATE OR REPLACE FUNCTION ledger_append_only() RETURNS TRIGGER AS $$
BEGIN RAISE EXCEPTION 'ledger tables are append-only'; END $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_entry_append_only BEFORE UPDATE OR DELETE ON ledger_entry FOR EACH ROW EXECUTE FUNCTION ledger_append_only();
CREATE TRIGGER trg_txn_append_only   BEFORE UPDATE OR DELETE ON ledger_txn   FOR EACH ROW EXECUTE FUNCTION ledger_append_only();

CREATE MATERIALIZED VIEW account_balance AS
  SELECT a.id AS account_id, a.type, a.owner_type, a.owner_id,
         COALESCE(SUM(e.credit) - SUM(e.debit), 0) AS balance
  FROM ledger_account a LEFT JOIN ledger_entry e ON e.account_id = a.id
  GROUP BY a.id, a.type, a.owner_type, a.owner_id;
CREATE UNIQUE INDEX ux_account_balance ON account_balance (account_id);
-- REFRESH MATERIALIZED VIEW CONCURRENTLY account_balance;  (run after posting batches / every minute)

CREATE TABLE payout (
  id           TEXT PRIMARY KEY,
  payee_type   TEXT NOT NULL CHECK (payee_type IN ('VENDOR','CREATOR')),
  payee_id     TEXT NOT NULL,
  run_id       TEXT NOT NULL,
  amount       NUMERIC(12,2) NOT NULL CHECK (amount > 0),
  period_from  DATE NOT NULL,
  period_to    DATE NOT NULL,
  status       TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','APPROVED','PAID','FAILED')),
  bank_ref     TEXT,
  approved_by  TEXT,
  approved_at  TIMESTAMPTZ,
  paid_at      TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_payout_payee ON payout (payee_type, payee_id, created_at DESC);
CREATE INDEX ix_payout_run ON payout (run_id);

CREATE TABLE payout_run (
  id           TEXT PRIMARY KEY,
  payee_type   TEXT NOT NULL CHECK (payee_type IN ('VENDOR','CREATOR')),
  period_from  DATE NOT NULL,
  period_to    DATE NOT NULL,
  status       TEXT NOT NULL DEFAULT 'PREVIEW' CHECK (status IN ('PREVIEW','APPROVED','PAID')),
  total_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
  csv_key      TEXT,
  created_by   TEXT NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE remittance_import (
  id           TEXT PRIMARY KEY,
  courier      TEXT NOT NULL,
  file_key     TEXT NOT NULL,
  row_count    INT NOT NULL DEFAULT 0,
  matched      INT NOT NULL DEFAULT 0,
  unmatched    INT NOT NULL DEFAULT 0,
  total_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
  txn_id       TEXT REFERENCES ledger_txn(id),
  imported_by  TEXT NOT NULL,
  imported_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE remittance_row (
  id            BIGSERIAL PRIMARY KEY,
  import_id     TEXT NOT NULL REFERENCES remittance_import(id) ON DELETE CASCADE,
  tracking_no   TEXT,
  amount        NUMERIC(12,2) NOT NULL,
  shipment_id   TEXT REFERENCES shipment(id),
  match_status  TEXT NOT NULL DEFAULT 'UNMATCHED' CHECK (match_status IN ('AUTO','MANUAL','UNMATCHED','FEE')),
  raw           JSONB
);

-- ---------------------------------------------------------------------
-- 8. Notifications & outbox
-- ---------------------------------------------------------------------
CREATE TABLE outbox (
  id            TEXT PRIMARY KEY,
  aggregate_type TEXT NOT NULL,
  aggregate_id  TEXT NOT NULL,
  event_type    TEXT NOT NULL,                       -- OrderConfirmed, OrderDelivered, ...
  payload       JSONB NOT NULL,
  occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  dispatched_at TIMESTAMPTZ,
  attempts      INT NOT NULL DEFAULT 0,
  last_error    TEXT
);
CREATE INDEX ix_outbox_pending ON outbox (occurred_at) WHERE dispatched_at IS NULL;

CREATE TABLE processed_event (               -- consumer idempotency (FR-NOT-04)
  consumer   TEXT NOT NULL,
  event_id   TEXT NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (consumer, event_id)
);

CREATE TABLE message_template (
  key       TEXT NOT NULL,
  language  CHAR(2) NOT NULL,
  channel   TEXT NOT NULL CHECK (channel IN ('SMS','WHATSAPP','EMAIL')),
  body      TEXT NOT NULL,
  PRIMARY KEY (key, language, channel)
);

CREATE TABLE message_log (
  id          BIGSERIAL PRIMARY KEY,
  event_id    TEXT,
  template_key TEXT NOT NULL,
  channel     TEXT NOT NULL,
  recipient   TEXT NOT NULL,
  language    CHAR(2),
  status      TEXT NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','SENT','DELIVERED','FAILED')),
  provider_ref TEXT,
  error       TEXT,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  sent_at     TIMESTAMPTZ
);
CREATE INDEX ix_msglog_recipient ON message_log (recipient, created_at DESC);  -- purge > 90 days

CREATE TABLE marketing_optin (
  phone       TEXT PRIMARY KEY,
  opted_in    BOOLEAN NOT NULL DEFAULT FALSE,
  language    CHAR(2) NOT NULL DEFAULT 'en',
  last_sent_at TIMESTAMPTZ,
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 9. Scheduler lock (ShedLock)
-- ---------------------------------------------------------------------
CREATE TABLE shedlock (
  name       VARCHAR(64) PRIMARY KEY,
  lock_until TIMESTAMPTZ NOT NULL,
  locked_at  TIMESTAMPTZ NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);

-- ---------------------------------------------------------------------
-- 10. Shipping fee table (BR-04)
-- ---------------------------------------------------------------------
CREATE TABLE shipping_fee (
  district  TEXT PRIMARY KEY,
  fee       NUMERIC(12,2) NOT NULL,
  active    BOOLEAN NOT NULL DEFAULT TRUE,
  eta_days  INT NOT NULL DEFAULT 2
);
INSERT INTO shipping_fee (district, fee, eta_days) VALUES
 ('Colombo', 300, 1), ('Gampaha', 350, 1), ('Kalutara', 350, 2);

-- ---------------------------------------------------------------------
-- 11. updated_at maintenance
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION touch_updated_at() RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END $$ LANGUAGE plpgsql;
DO $$ DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['app_user','vendor','product','creator','"order"','shipment','payment','setting','marketing_optin']
  LOOP EXECUTE format('CREATE TRIGGER trg_touch_%s BEFORE UPDATE ON %s FOR EACH ROW EXECUTE FUNCTION touch_updated_at()', replace(t,'"',''), t);
  END LOOP;
END $$;
