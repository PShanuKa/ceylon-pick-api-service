# CeylonPick backend — build plan

A living checklist for the v1 backend. `doc/version 1/` holds the frozen specification; this file
tracks what has been built against it and what is next. **Update the status table and the module
checklists as work lands** — that is the point of the file.

Every requirement id below is quoted from the SRS or the Technical Architecture. Where this file and
those documents disagree, they win.

---

## Status

| # | Module | Package | Status | Exit criterion (Architecture §12) |
|---|---|---|---|---|
| 1 | Shared kernel + auth | `shared`, `auth` | **Done** | Deploys; health endpoint green; login works |
| 2 | Settings, vendors, catalog | `settings`, `vendors`, `catalog` | **Done** (bar R2 upload) | 20 real products listed with photos |
| 3 | Orders + notifications adapter | `orders`, `notifications` | Not started | End-to-end COD order confirmed by OTP on a real phone |
| 4 | Payments | `payments` | Not started | Sandbox prepaid order reaches CONFIRMED via IPN only |
| 5 | Ledger + payouts | `ledger` | Not started | Fig. 6 reproduces exactly; bank-vs-held check passes |
| 6 | Creators + attribution | `creators` | Not started | Attributed order settles into `CREATOR_PAYABLE` |
| 7 | Shipment, courier, disputes | `orders` (shipping), adapters | Not started | Full `PLACED → SETTLED` and `PLACED → RTO` with a real courier |
| 8 | Hardening | — | Not started | Go-live checklist signed; 20 vendors, 20 creators onboarded |

The order is Architecture §12's, and the reason for it is stated there: *"Money-critical paths are
built first and tested first. Front-end polish comes last."* Do not reorder to do the easy modules
first — every later module posts to the ledger, so the ledger's shape has to be right before much
is built on top of it.

Hard rule from the Project Plan §11: *"if v1 is not live by week 8, switch to WooCommerce and ship
anyway."*

---

## 1. Shared kernel + auth — **done**

Built: `lk.ceylonpick.shared` (ULID `Ids`, `Hashes`, `Phones`, `Clock`, `ApiResponse` envelope,
`ApiException` + `GlobalExceptionHandler`, `PageQuery`/`DateRangeQuery`, EN/SI/TA `MessageResolver`)
and `lk.ceylonpick.auth` (identity, sessions, OTP, admin accounts). `V1__auth.sql` is applied.

Satisfies FR-AUTH-01..05, BR-07 (OTP limits), FR-NOT-01's rate cap, and the AT-19 security
assertions that vendor A cannot read vendor B's data and that admin login requires OTP.

Still outstanding in this area:

- [ ] `Money` — the shared kernel is specified to own it (BigDecimal, LKR, scale 2, `HALF_EVEN`).
      Not written yet; the ledger needs it first.
- [x] `audit_log` moved to `shared/audit` — NFR-07 covers settings changes and admin actions, not
      only auth, so every module writes through the same `AuditService`.
- [ ] `DomainEvent` / `OutboxEntry` / `IdempotencyKey` — also shared-kernel, needed by notifications.
- [ ] Real SMS/WhatsApp sender behind `OtpSender`; today it logs the code.
- [ ] A mailer for password-reset and email-verification links; today they are logged.
- [ ] Tamil bundle reviewed by a native speaker (NFR-06 makes it published content).
- [ ] Testcontainers integration tests — blocked on Docker not running on this machine.

---

## 2. Settings, vendors and catalog — **done** (bar R2 upload)

**Owns** — from `doc/version 1/V1__init.sql` §1, §2, §3 and §10: `setting`, `vendor`,
`vendor_verification`, `category`, `product`, `product_image`, `product_variant`,
`stock_reservation`, `shipping_fee`. Applied as `V2__catalog_vendors.sql`.

**Public API** (Architecture §4):
`catalog`: `getProduct(slug)`, `searchProducts(q, category)`, `reserveStock(variantId, qty)`, `releaseStock()`
`vendors`: `getVendor(id)`, `listPayableVendors()`, `verificationQueue()`

**Requirements** — FR-CAT-01..07, FR-VEN-01..09, FR-ADM-03 (moderation).

**Rules** — BR-02 creator commission bounds 10–20%; BR-06 prepaid-only items; BR-20 caps of 25
vendors and 15 SKUs each; BR-21 new or changed products need admin moderation before going live;
BR-23 three vendor strikes trigger review.

- [x] `setting` table and a cached, typed `Settings` API — BR-02 and BR-20 are configurable values
      with an audit trail (SRS §3), not constants
- [x] `vendor` + `vendor_verification`, hanging off `app_user(id)` as `admin_profile` does
- [x] Vendor application (public, no account) → admin queue → three checks → VERIFIED (FR-VEN-01/02).
      Verifying is refused with 409 until documents, sample and call are all recorded, and is where
      the vendor's login is first provisioned
- [x] Product/variant CRUD with the moderation gate. A vendor can only push towards moderation;
      LIVE is reachable only through an admin approval, and editing a LIVE product sends it back
- [x] Stock reservation with TTL (FR-CAT-04), proven under 24 concurrent buyers by
      `StockServiceConcurrencyTest`
- [x] SKU cap with an explanatory message (FR-CAT-07), read from settings
- [x] Vendor suspension hides products immediately (FR-VEN-09)
- [ ] R2 pre-signed image upload — the API takes an object key today; no credentials to wire the
      signer against yet

Two notes in the original plan turned out to be wrong, and are corrected here:

- **`AuthUser` does not carry `vendorId`.** Populating it would mean auth reading the vendors
  table while vendors already depends on auth — a cycle. Each module resolves its own profile from
  `userId`; `VendorApi.requireOwnVendor` is where FR-AUTH-03 ownership is actually answered.
- **Vendor suspension does not evict the auth cache**, because it changes `vendor.status`, not
  `app_user.status`. FR-VEN-09 pauses new orders "while existing orders complete", so the login
  must keep working. Visibility comes from catalog filtering on `VendorApi.sellableVendorIds()`,
  read live. Creator auto-pause (BR-16) will be the same shape.

---

## 3. Orders + notifications

**Owns** — `"order"`, `order_item`, `order_status_history`, `otp_challenge`'s `ORDER_CONFIRM`
purpose, `shipment`, `dispute`; and `outbox`, `processed_event`, `message_template`, `message_log`,
`marketing_optin`.

**Public API**: `orders`: `placeOrder(cmd)`, `confirmOtp(orderId, code)`, `markPacked(orderId, photoKey)`,
`markShipped(orderId, tracking)`, `applyCourierStatus()`, `openDispute()`.
`notifications`: `send(template, to, vars)` — *"consumed via events only"*.

**Requirements** — FR-ORD-01..14, FR-NOT-01..05.

**Rules** — BR-04 shipping fee by district; BR-05 COD cap (LKR 5,000; beauty 3,000); BR-07 OTP;
BR-08 prepaid auto-cancel at 30 min; BR-09 packing photo required; BR-10 seven-day settlement hold;
BR-11 48-hour dispute window; BR-19 one vendor per order; BR-25 no transition outside the table.

### The state machine

Architecture §6 Figure 5 is **normative** — read it before writing `OrderStateMachine`. The summary
below is a reading of that table, kept here so the shape is visible while planning:

| From | To | Trigger | Guards | Effects |
|---|---|---|---|---|
| — | PLACED | buyer checkout | single vendor; stock available; COD allowed and within cap | reserve stock; freeze attribution |
| PLACED | AWAITING_PAYMENT | system, prepaid | — | create PaymentIntent, redirect to PayHere |
| PLACED | AWAITING_OTP | system, COD | — | send OTP |
| AWAITING_PAYMENT | CONFIRMED | PayHere IPN status=2 | signature valid; amount matches; idem key unseen | notify; Dr GATEWAY_CLEARING / Cr HELD_FUNDS |
| AWAITING_PAYMENT | CANCELLED | IPN failed or 30-min job | — | release stock |
| AWAITING_OTP | CONFIRMED | buyer enters OTP | code matches, not expired, attempts < 3 | notify vendor + buyer |
| AWAITING_OTP | CANCELLED | 24 h job or 3 failed attempts | — | release stock; counts as "unconfirmed", not RTO |
| CONFIRMED | PACKED | vendor | packing photo uploaded | notify admin for pickup batch |
| CONFIRMED | CANCELLED | vendor request + admin approval | reason recorded | release stock; refund if prepaid; vendor strike |
| PACKED | SHIPPED | vendor/admin enters tracking | courier accepted | notify buyer with tracking |
| SHIPPED | DELIVERED | courier status or admin | — | start 7-day hold; COD: Dr COURIER_CLEARING / Cr HELD_FUNDS |
| SHIPPED | RTO | courier returned | — | return fee per vendor policy; increment creator RTO; stock back |
| DELIVERED | DISPUTED | buyer, within 48 h | photo evidence attached | pause settlement |
| DELIVERED | SETTLED | settlement job at delivered_at + 7d | no open dispute | ledger split |
| DISPUTED | SETTLED / REFUNDED / RTO | admin decision | — | per outcome |

Terminal: SETTLED, CANCELLED, RTO, REFUNDED.

- [ ] `OrderStateMachine` as one class with an explicit transition table; *"any attempt outside the
      table throws"*. An invalid transition returns **409 with the allowed targets** (FR-ORD-11)
- [ ] `order_status_history` written on every transition with from, to, actor, reason (FR-ORD-10)
- [ ] Cart spanning N vendors becomes N orders (BR-19, FR-ORD-01)
- [ ] COD hidden with a reason when over the cap or the cart holds a prepaid-only item (FR-ORD-02)
- [ ] OTP confirmation reusing the existing `otp_challenge` with purpose `ORDER_CONFIRM` — **add the
      FKs to `"order"(id)` from `otp_challenge.order_id` AND `stock_reservation.order_id` in this
      migration**; V1 and V2 both left them out because the table did not exist yet
- [ ] Wire `CatalogApi.reserveStock/releaseStock/consumeStock` into the state machine, and schedule
      `StockService.releaseExpired()` under ShedLock
- [ ] Scheduled jobs under ShedLock: 24 h OTP auto-cancel, 30 min prepaid timeout, hourly settlement
- [ ] Tracking by order number **without login** (FR-ORD-12) — a public route, so add it to
      `SecurityConfig`'s allowlist deliberately, not by accident
- [ ] Kill switch disabling new orders while tracking stays up (FR-ORD-14, BR: `kill_switch_new_orders`)
- [ ] Outbox + poller; every consumer idempotent on `event_id` (FR-NOT-04)
- [ ] Buyer messages in the buyer's language (FR-NOT-02) — the `MessageResolver` already exists

**Done when** AT-01 (COD happy path), AT-03 (3 wrong codes), AT-04 (24 h timeout), AT-10 (two-vendor
cart), AT-11 (COD above cap) pass.

---

## 4. Payments

**Owns** — `payment`, `ipn_log`. **API**: `createIntent(order)`, `handleIpn(payload)`, `verifySignature()`.
*"`payments` is the only module that talks to PayHere."*

**Requirements** — FR-PAY-01..05. **Rules** — BR-08.

- [ ] `POST /webhooks/payhere` — the one HTTP path the architecture fixes. *"verify md5sig, verify
      amount and currency against the intent, upsert Payment by payment_id (idempotent), then call
      `orders.confirmPayment()`. Respond 200 quickly"* — within 2 s (IF-02)
- [ ] Already exempt from CSRF in `SecurityConfig` (`/webhooks/**`); it authenticates by signature
- [ ] **The browser return URL never confirms an order** (FR-ORD-04). Success on return with no IPN
      leaves the order pending
- [ ] Idempotency on `payment_id` — a replayed IPN gives 200, no second state change, no second
      ledger transaction (AT-13)
- [ ] 30-minute timeout job cancels and releases stock
- [ ] Refunds recorded with a reference; `REFUNDED` requires the refund reference field
- [ ] Daily reconciliation against the PayHere settlement report, exceptions listed

**Done when** AT-02 (prepaid happy path) and AT-13 (duplicate IPN replay) pass.

---

## 5. Ledger + payouts

**Owns** — `ledger_account`, `ledger_txn`, `ledger_entry`, `account_balance`, `payout`, `payout_run`,
`remittance_import`, `remittance_row`. **API**: `post(txn)`, `balance(accountId)`,
`payoutPreview(period)`, `runPayout()`, `importCourierRemittance(csv)`.

*"`ledger` is the only module that writes ledger entries."* The DDL in `V1__init.sql` §7 already has
the deferred balanced-transaction trigger and the append-only guard; take it as written.

**Requirements** — FR-LED-01..09. **Rules** — BR-01 platform 6%; BR-02 creator 10–20%; BR-03 gateway
fee; BR-10 seven-day hold; BR-14 creator payouts on the 1st and 16th above LKR 2,000; BR-15 vendor
payouts Friday with Thursday cutoff; BR-22 RTO fee; BR-24 append-only.

- [ ] Balanced double-entry enforced **by the database**, not only by service code
- [ ] No `UPDATE`/`DELETE` grants on ledger tables for the application role — the trigger is a
      backstop, the grant is the control (FR-LED-02)
- [ ] Settlement split idempotent per order: *"Re-running settlement job posts nothing new; rounding
      absorbed by vendor share (HALF_EVEN, 2 dp)"* — hence `ledger_txn.idem_key`
- [ ] Remittance CSV import with auto-match, manual match, and unmatched rows that stay listed
- [ ] Reconciliation: entered bank balance vs `HELD_FUNDS + PLATFORM_REVENUE − payouts`
- [ ] Payout run: preview → approve (locks, generates bank CSV) → mark paid with references
- [ ] Payable balances visible to vendors and creators within a minute

**Done when** the Architecture Fig. 6 worked example reproduces **exactly**, and AT-14 (settlement
re-run), AT-16 (remittance with an unmatched row) and AT-17 (payout run) pass.

---

## 6. Creators + attribution

**Owns** — `creator`, `creator_pick`, `attribution_link`, `click`. **API**:
`resolveAttribution(code|cookie)`, `recordClick()`, `getStorefront(handle)`, `recomputeRtoRate(creatorId)`.

**Requirements** — FR-CRE-01..09 (FR-CRE-10 Campaign Board is v2). **Rules** — BR-16 auto-pause at
RTO > 30%, remove at > 40%, minimum 10 orders; BR-17 30-day cookie, last click wins, code at checkout
overrides, frozen at PLACED; BR-18 self-referral blocked by phone match.

- [ ] Creator application → admin approval (FR-CRE-01, FR-ADM-06)
- [ ] Public storefront `/@handle`, up to 24 picks with a note each
- [ ] Unique code plus product-specific links carrying it
- [ ] Attribution resolution: cookie, `?ref=CODE`, and a code typed at checkout that overrides
- [ ] Nightly RTO recompute with auto-pause and notification — **must evict the auth cache** when it
      pauses someone, for the same reason vendor suspension must
- [ ] BR-18: an order whose buyer phone equals the creator's phone is *"stored without attribution
      and flagged"*, not rejected. `Phones.normalise` already makes the comparison reliable
- [ ] Creators see masked buyer identity only — *"No buyer names or phones shown to creators"*
- [ ] `click` rows purged after 90 days (NFR-06); IPs hashed, which `Hashes.ipHash` already does

**Done when** AT-18 (self-referral) and AT-15 (creator auto-pause) pass, and an attributed order
settles into `CREATOR_PAYABLE`.

---

## 7. Shipment, courier and disputes

- [ ] Courier adapter with a manual path first. Architecture's go-live checklist is explicit:
      *"Admin can manually move any order between allowed states with a reason (courier APIs will
      fail)."* Build manual entry before any integration
- [ ] Shipment status feed or poll → `DELIVERED` / `RTO`
- [ ] Dispute flow with 1–3 photos inside the 48-hour window; admin resolves to SETTLED, REFUNDED or
      RTO, each posting the correct ledger transaction
- [ ] RTO fee per the vendor's `rto_fee_policy` (FLAT / SPLIT / NONE)

**Done when** AT-05 (RTO), AT-06 (dispute inside the window) and AT-07 (dispute after it) pass.

---

## 8. Hardening

- [ ] ShedLock on every scheduled job (single-run safety)
- [ ] Load test: 100 concurrent users without errors
- [ ] Security pass: OWASP Top 10 checklist signed before go-live (NFR-05)
- [ ] Nightly `pg_dump` to R2, **verified by a restore test**
- [ ] Sentry, structured JSON logs, UptimeRobot
- [ ] Data seed for 20 vendors
- [ ] 80% unit coverage on the state machine and the ledger (NFR-11)
- [ ] `ApplicationModules.verify()` in CI if Spring Modulith is adopted — the module boundaries are
      currently a convention, not an enforced one

---

## Decisions carried forward

These were settled during the auth build and apply to everything after it. `CLAUDE.md` has the
detail; the short version:

- One `app_user` for identity, one profile table per actor. `vendor` and `creator` join
  `admin_profile` and `buyer_profile` in that pattern.
- The access token carries only the user id; role and status are read live. **Anything that changes
  a person's role or status must evict the auth cache** — the scheduled jobs in modules 2 and 6 are
  the first real test of that.
- Buyers may hold an optional email and password (a deliberate departure from FR-AUTH-01).
- Admins are split into OWNER and MANAGER (an extension; SRS §2.2 has one flat admin).
- Errors go through `ApiException` with a stable code; copy lives in `messages*.properties`.
- Migrations are per module, numbered in build order. `V1__auth.sql` and `V2__catalog_vendors.sql`
  are applied; module 3 starts at `V3__`.
- A module never reads another module's tables. Where catalog needs vendor state it calls
  `VendorApi.sellableVendorIds()`; that is only reasonable while BR-20 caps vendors at 25, and
  becomes a read model if the cap lifts.

## Open items from the spec

SRS §10 and Architecture §15 list issues the documents themselves leave open. Two that will block
work if not resolved in time: the RTO fee amount (BR-22 is marked *"to confirm"*) and which courier
provides a status feed rather than only a CSV.
