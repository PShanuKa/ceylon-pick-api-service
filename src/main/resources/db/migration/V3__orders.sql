-- =====================================================================
-- CeylonPick V3 — orders, fulfilment, and the event outbox.
--
-- From doc/version 1/V1__init.sql sections 5, 8 and 9. This is where the
-- two foreign keys V1 and V2 had to leave out finally get added: both
-- pointed at "order", which did not exist yet.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Outbox (shared kernel infrastructure, Architecture §4)
--
-- "Events are persisted in the outbox in the same transaction as the state
-- change, then dispatched by a poller (at-least-once). Every consumer is
-- idempotent on event_id."
-- ---------------------------------------------------------------------
CREATE TABLE outbox (
  id             TEXT PRIMARY KEY,               -- this is the event id
  aggregate_type TEXT NOT NULL,
  aggregate_id   TEXT NOT NULL,
  event_type     TEXT NOT NULL,                  -- OrderConfirmed, OrderDelivered, ...
  payload        JSONB NOT NULL,
  occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  dispatched_at  TIMESTAMPTZ,
  attempts       INT NOT NULL DEFAULT 0,
  last_error     TEXT
);
CREATE INDEX ix_outbox_pending ON outbox (occurred_at) WHERE dispatched_at IS NULL;
CREATE INDEX ix_outbox_aggregate ON outbox (aggregate_type, aggregate_id);

-- FR-NOT-04. The composite primary key is the idempotency mechanism: a second
-- claim of the same event by the same consumer fails the insert.
CREATE TABLE processed_event (
  consumer     TEXT NOT NULL,
  event_id     TEXT NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (consumer, event_id)
);

-- ---------------------------------------------------------------------
-- 2. Orders
--
-- BR-19: one vendor per order. A cart spanning N vendors becomes N orders,
-- which is why vendor_id sits on the order itself and not only on the line.
-- ---------------------------------------------------------------------
CREATE TABLE "order" (
  id                 TEXT PRIMARY KEY,
  number             TEXT NOT NULL UNIQUE,             -- CP-10231
  vendor_id          TEXT NOT NULL REFERENCES vendor(id),
  -- Null for a guest: the phone is the identity (Architecture §11.1), and an
  -- account is only created if the buyer signs in later.
  buyer_user_id      TEXT REFERENCES app_user(id),
  buyer_phone        TEXT NOT NULL,
  buyer_name         TEXT NOT NULL,
  address            JSONB NOT NULL,                   -- {line, city, district, landmark}
  district           TEXT NOT NULL,
  language           TEXT NOT NULL DEFAULT 'en' CHECK (language IN ('en','si','ta')),
  pay_method         TEXT NOT NULL CHECK (pay_method IN ('COD','PREPAID')),
  status             TEXT NOT NULL CHECK (status IN (
                       'PLACED','AWAITING_PAYMENT','AWAITING_OTP','CONFIRMED','PACKED','SHIPPED',
                       'DELIVERED','SETTLED','DISPUTED','RTO','CANCELLED','REFUNDED')),
  subtotal           NUMERIC(12,2) NOT NULL,
  shipping_fee       NUMERIC(12,2) NOT NULL DEFAULT 0,
  total              NUMERIC(12,2) NOT NULL,
  -- BR-17: attribution is frozen at PLACED, so a later click cannot move
  -- commission from one creator to another after the fact.
  creator_id         TEXT,
  attribution_type   TEXT CHECK (attribution_type IN ('LINK','CODE')),
  self_referral_flag BOOLEAN NOT NULL DEFAULT FALSE,   -- BR-18
  packing_photo_key  TEXT,                             -- BR-09
  cancel_reason      TEXT,
  version            INT NOT NULL DEFAULT 0,           -- optimistic lock
  placed_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  confirmed_at       TIMESTAMPTZ,
  packed_at          TIMESTAMPTZ,
  shipped_at         TIMESTAMPTZ,
  delivered_at       TIMESTAMPTZ,
  settled_at         TIMESTAMPTZ,
  closed_at          TIMESTAMPTZ,
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (total = subtotal + shipping_fee)
);
-- The FK to creator(id) is added by the creators migration; that table does
-- not exist yet.
CREATE INDEX ix_order_status ON "order" (status, updated_at);
CREATE INDEX ix_order_vendor ON "order" (vendor_id, status);
CREATE INDEX ix_order_creator ON "order" (creator_id, placed_at) WHERE creator_id IS NOT NULL;
CREATE INDEX ix_order_buyer_phone ON "order" (buyer_phone, placed_at DESC);
-- Drives the hourly settlement job (FR-ORD-07).
CREATE INDEX ix_order_settle_due ON "order" (delivered_at) WHERE status = 'DELIVERED';

CREATE SEQUENCE order_number_seq START 10001;

-- Prices and percentages are snapshots. SRS §3: settings "apply to orders
-- placed after the change", so an order settles on the numbers it was placed
-- with, whatever the settings say months later.
CREATE TABLE order_item (
  id             TEXT PRIMARY KEY,
  order_id       TEXT NOT NULL REFERENCES "order"(id) ON DELETE CASCADE,
  variant_id     TEXT NOT NULL REFERENCES product_variant(id),
  product_id     TEXT NOT NULL REFERENCES product(id),
  vendor_id      TEXT NOT NULL REFERENCES vendor(id),
  title_snapshot TEXT NOT NULL,
  qty            INT NOT NULL CHECK (qty > 0),
  unit_price     NUMERIC(12,2) NOT NULL,
  creator_pct    NUMERIC(5,2) NOT NULL,                -- snapshot (BR-02)
  platform_pct   NUMERIC(5,2) NOT NULL,                -- snapshot (BR-01)
  line_total     NUMERIC(12,2) GENERATED ALWAYS AS (qty * unit_price) STORED
);
CREATE INDEX ix_item_order ON order_item (order_id);

-- FR-ORD-10: every state change recorded with from, to, actor, reason and time.
-- The buyer's tracking timeline and the admin audit both read from here.
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

-- ---------------------------------------------------------------------
-- 3. Fulfilment
-- ---------------------------------------------------------------------
CREATE TABLE shipment (
  id              TEXT PRIMARY KEY,
  order_id        TEXT NOT NULL UNIQUE REFERENCES "order"(id) ON DELETE CASCADE,
  courier         TEXT NOT NULL,                       -- KOOMBIYO | PROMPT_XPRESS | PRONTO | MANUAL
  tracking_no     TEXT,
  status          TEXT NOT NULL DEFAULT 'CREATED'
                    CHECK (status IN ('CREATED','IN_TRANSIT','FAILED_ATTEMPT','DELIVERED','RETURNED')),
  failed_attempts INT NOT NULL DEFAULT 0,
  cod_amount      NUMERIC(12,2) NOT NULL DEFAULT 0,
  remitted_at     TIMESTAMPTZ,
  remittance_ref  TEXT,
  return_fee      NUMERIC(12,2),                       -- BR-22
  raw_status      TEXT,                                -- whatever the courier actually said
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_shipment_tracking ON shipment (courier, tracking_no);
-- What the courier still owes us in collected COD (FR-LED-05).
CREATE INDEX ix_shipment_unremitted ON shipment (courier)
  WHERE status = 'DELIVERED' AND remitted_at IS NULL;

-- BR-11: 48 hours from delivery, with photo evidence. An open dispute pauses
-- settlement (FR-ORD-08).
CREATE TABLE dispute (
  id              TEXT PRIMARY KEY,
  order_id        TEXT NOT NULL UNIQUE REFERENCES "order"(id),
  reason          TEXT NOT NULL CHECK (reason IN ('DAMAGED','WRONG_ITEM','MISSING_ITEM','OTHER')),
  note            VARCHAR(200),
  photo_keys      TEXT[] NOT NULL,
  status          TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','REJECTED','REFUNDED','RETURNED')),
  decision_reason TEXT,
  decided_by      TEXT REFERENCES app_user(id),
  opened_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  decided_at      TIMESTAMPTZ
);

-- ---------------------------------------------------------------------
-- 4. Notifications (FR-NOT-02/03/05)
-- ---------------------------------------------------------------------
CREATE TABLE message_template (
  key      TEXT NOT NULL,
  language TEXT NOT NULL CHECK (language IN ('en','si','ta')),
  channel  TEXT NOT NULL CHECK (channel IN ('SMS','WHATSAPP','EMAIL')),
  body     TEXT NOT NULL,
  PRIMARY KEY (key, language, channel)
);

CREATE TABLE message_log (
  id           BIGSERIAL PRIMARY KEY,
  event_id     TEXT,
  template_key TEXT NOT NULL,
  channel      TEXT NOT NULL,
  recipient    TEXT NOT NULL,
  language     TEXT,
  status       TEXT NOT NULL DEFAULT 'QUEUED'
                 CHECK (status IN ('QUEUED','SENT','DELIVERED','FAILED')),
  provider_ref TEXT,
  error        TEXT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  sent_at      TIMESTAMPTZ
);
CREATE INDEX ix_msglog_recipient ON message_log (recipient, created_at DESC);  -- purge > 90 days

-- FR-NOT-05: marketing needs opt-in and at most one message a week.
CREATE TABLE marketing_optin (
  phone        TEXT PRIMARY KEY,
  opted_in     BOOLEAN NOT NULL DEFAULT FALSE,
  language     TEXT NOT NULL DEFAULT 'en' CHECK (language IN ('en','si','ta')),
  last_sent_at TIMESTAMPTZ,
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 5. Scheduler lock (ShedLock)
--
-- Architecture §3: "Spring @Scheduled + DB-backed outbox/job table (ShedLock
-- for single-run safety)". The settlement and auto-cancel jobs move money and
-- state, so a second instance must never run them concurrently.
-- ---------------------------------------------------------------------
CREATE TABLE shedlock (
  name       VARCHAR(64) PRIMARY KEY,
  lock_until TIMESTAMPTZ NOT NULL,
  locked_at  TIMESTAMPTZ NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);

-- ---------------------------------------------------------------------
-- 6. The foreign keys V1 and V2 had to defer
-- ---------------------------------------------------------------------
ALTER TABLE otp_challenge
  ADD CONSTRAINT fk_otp_order FOREIGN KEY (order_id) REFERENCES "order"(id) ON DELETE CASCADE;

ALTER TABLE stock_reservation
  ADD CONSTRAINT fk_reservation_order FOREIGN KEY (order_id) REFERENCES "order"(id);

-- ---------------------------------------------------------------------
-- 7. updated_at maintenance
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_touch_order    BEFORE UPDATE ON "order"          FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER trg_touch_shipment BEFORE UPDATE ON shipment         FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER trg_touch_optin    BEFORE UPDATE ON marketing_optin  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
