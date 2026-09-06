-- =====================================================================
-- CeylonPick V1 — auth module only.
-- Catalog, orders, ledger and payouts arrive in later migrations; the
-- column names/types here match doc/version 1/V1__init.sql so those
-- migrations line up.
-- Conventions: ULID text ids, TIMESTAMPTZ (UTC), snake_case.
--
-- Deviations from the reference schema: email is TEXT, not CITEXT (the
-- application lower-cases it before writing, which avoids the
-- citext<->varchar JDBC binding friction and drops an extension), and
-- language is TEXT + CHECK rather than CHAR(2), which avoids bpchar's
-- blank-padding and its awkward JPA type mapping.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Identity & credentials
--
-- One row per person. The profile tables below hang off it, so someone
-- who is both a creator and a buyer keeps one identity and one phone —
-- which BR-18 (self-referral: creator phone <> buyer phone) depends on.
--
-- Credential shapes this table must legally hold:
--   BUYER  phone only, no email, no password   (guest checkout, FR-AUTH-01)
--   BUYER  phone + email + password            (optional customer account)
--   staff  email + password (+ OTP)            (FR-AUTH-02)
-- ---------------------------------------------------------------------
CREATE TABLE app_user (
  id                      TEXT PRIMARY KEY,
  email                   TEXT,
  phone                   TEXT,
  password_hash           TEXT,
  role                    TEXT NOT NULL CHECK (role IN ('ADMIN','CREATOR','VENDOR','BUYER')),
  status                  TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','LOCKED','DISABLED')),
  two_factor_enabled      BOOLEAN NOT NULL DEFAULT FALSE,   -- FR-AUTH-02 optional OTP; always on for ADMIN
  failed_logins           INT NOT NULL DEFAULT 0,
  locked_until            TIMESTAMPTZ,
  email_verified_at       TIMESTAMPTZ,
  phone_verified_at       TIMESTAMPTZ,
  -- Access tokens issued before this instant are rejected. Bumped on
  -- logout-all, password change and admin force-logout; this is what makes
  -- revocation immediate without putting an extra claim in the JWT.
  sessions_invalidated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_login_at           TIMESTAMPTZ,
  language                TEXT NOT NULL DEFAULT 'en' CHECK (language IN ('en','si','ta')),
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ux_app_user_email UNIQUE (email),
  CONSTRAINT ck_app_user_staff_creds
    CHECK (role = 'BUYER' OR (email IS NOT NULL AND password_hash IS NOT NULL)),
  CONSTRAINT ck_app_user_buyer_phone
    CHECK (role <> 'BUYER' OR phone IS NOT NULL),
  CONSTRAINT ck_app_user_password_needs_email
    CHECK (password_hash IS NULL OR email IS NOT NULL)
);
CREATE UNIQUE INDEX ux_app_user_phone_buyer ON app_user (phone) WHERE role = 'BUYER';
CREATE INDEX ix_app_user_role_status ON app_user (role, status);

-- ---------------------------------------------------------------------
-- 2. Per-actor profiles
--
-- The vendor and creator profiles arrive with their own modules'
-- migrations (doc/version 1/V1__init.sql sections 2 and 4); both hang off
-- app_user(id) exactly as these do.
-- ---------------------------------------------------------------------

-- An extension beyond the SRS, which models Admin as a single flat role
-- ("Founder/operator role with full access", SRS 2.2).
CREATE TABLE admin_profile (
  user_id    TEXT PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
  admin_role TEXT NOT NULL CHECK (admin_role IN ('OWNER','MANAGER')),
  full_name  TEXT NOT NULL,
  created_by TEXT REFERENCES app_user(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_admin_profile_role ON admin_profile (admin_role);

-- SRS 7 buyer data: phone (identity), name, address, district, language,
-- WhatsApp opt-in. Language lives on app_user. One saved address, not an
-- address book (UI Spec 5, Preferences).
CREATE TABLE buyer_profile (
  user_id        TEXT PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
  full_name      TEXT,
  saved_address  JSONB,                              -- {line, city, district, landmark}
  district       TEXT,
  whatsapp_optin BOOLEAN NOT NULL DEFAULT FALSE,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- FR-AUTH-05: record acceptance of creator/vendor terms with version and timestamp.
CREATE TABLE terms_acceptance (
  id          TEXT PRIMARY KEY,
  user_id     TEXT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  terms_type  TEXT NOT NULL CHECK (terms_type IN ('CREATOR','VENDOR','BUYER_PRIVACY','BUYER_TERMS')),
  version     TEXT NOT NULL,
  accepted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ip_hash     TEXT                                   -- NFR-06: IPs hashed, never stored raw
);
CREATE INDEX ix_terms_user ON terms_acceptance (user_id, terms_type, accepted_at DESC);

-- ---------------------------------------------------------------------
-- 3. OTP challenges (BR-07, FR-AUTH-01/02/04)
--
-- 6 digits, code_hash only (SHA-256 + salt), 10-minute expiry, max 3
-- attempts, max 3 resends. The 3-per-phone-per-hour cap (FR-NOT-01 /
-- IF-04) is a COUNT over created_at, so it survives restarts and stays
-- correct across instances — unlike an in-process rate limiter.
-- ---------------------------------------------------------------------
CREATE TABLE otp_challenge (
  id           TEXT PRIMARY KEY,
  purpose      TEXT NOT NULL CHECK (purpose IN
                 ('ORDER_CONFIRM','BUYER_LOGIN','ADMIN_LOGIN','LOGIN_2FA','BANK_EDIT','PHONE_VERIFY')),
  -- The FK to "order"(id) is added by the orders migration; that table
  -- does not exist yet.
  order_id     TEXT,
  user_id      TEXT REFERENCES app_user(id) ON DELETE CASCADE,
  phone        TEXT NOT NULL,
  code_hash    TEXT NOT NULL,                        -- sha256(salt || code)
  salt         TEXT NOT NULL,
  channel      TEXT NOT NULL CHECK (channel IN ('SMS','WHATSAPP')),
  attempts     INT NOT NULL DEFAULT 0,
  resends      INT NOT NULL DEFAULT 0,
  expires_at   TIMESTAMPTZ NOT NULL,
  confirmed_at TIMESTAMPTZ,
  consumed_at  TIMESTAMPTZ,                          -- set when exchanged for a session
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_otp_phone_recent ON otp_challenge (phone, created_at DESC);
CREATE INDEX ix_otp_open ON otp_challenge (user_id, purpose, created_at DESC) WHERE confirmed_at IS NULL;

-- ---------------------------------------------------------------------
-- 4. Sessions
--
-- Access token = short-lived JWT carrying only the user id, in an httpOnly
-- cookie (Architecture 3). Refresh token = opaque, stored hashed, rotated
-- on every use. Presenting an already-rotated token means it leaked, so
-- the whole family is revoked.
-- ---------------------------------------------------------------------
CREATE TABLE refresh_token (
  id             TEXT PRIMARY KEY,
  user_id        TEXT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  token_hash     TEXT NOT NULL UNIQUE,               -- sha256 of the opaque token
  family_id      TEXT NOT NULL,
  issued_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at     TIMESTAMPTZ NOT NULL,
  revoked_at     TIMESTAMPTZ,
  revoked_reason TEXT,
  replaced_by_id TEXT REFERENCES refresh_token(id),
  user_agent     TEXT,
  ip_hash        TEXT
);
CREATE INDEX ix_refresh_user ON refresh_token (user_id, issued_at DESC);
CREATE INDEX ix_refresh_family ON refresh_token (family_id);
CREATE INDEX ix_refresh_live ON refresh_token (expires_at) WHERE revoked_at IS NULL;

CREATE TABLE password_reset_token (
  id         TEXT PRIMARY KEY,
  user_id    TEXT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at    TIMESTAMPTZ,
  ip_hash    TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_pwreset_user ON password_reset_token (user_id, created_at DESC);

CREATE TABLE email_verification_token (
  id         TEXT PRIMARY KEY,
  user_id    TEXT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  email      TEXT NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at    TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_emailverify_user ON email_verification_token (user_id, created_at DESC);

-- ---------------------------------------------------------------------
-- 5. Audit (NFR-07: admin actions and settings changes logged with actor and time)
-- ---------------------------------------------------------------------
CREATE TABLE audit_log (
  id          BIGSERIAL PRIMARY KEY,
  actor_id    TEXT,
  actor_role  TEXT,
  action      TEXT NOT NULL,          -- LOGIN_SUCCEEDED, LOGIN_FAILED, ACCOUNT_LOCKED, ROLE_CHANGED, ...
  entity_type TEXT,
  entity_id   TEXT,
  before      JSONB,
  after       JSONB,
  reason      TEXT,
  ip_hash     TEXT,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_entity ON audit_log (entity_type, entity_id, created_at DESC);
CREATE INDEX ix_audit_actor ON audit_log (actor_id, created_at DESC);

-- ---------------------------------------------------------------------
-- 6. updated_at maintenance
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION touch_updated_at() RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_touch_app_user      BEFORE UPDATE ON app_user      FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER trg_touch_admin_profile BEFORE UPDATE ON admin_profile FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER trg_touch_buyer_profile BEFORE UPDATE ON buyer_profile FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
