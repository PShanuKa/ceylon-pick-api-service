# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

CeylonPick's backend: a curated Sri Lankan marketplace where verified vendors sell through creator
storefronts. Spring Boot 4.1.1 / Java 21 / PostgreSQL, built as a modular monolith.

## Commands

```bash
./mvnw test                          # all tests
./mvnw test -Dtest=PhonesTest        # one class
./mvnw test -Dtest='PhonesTest#masksAllButTheLastThreeDigits'
./mvnw -DskipTests package           # build the jar
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
java -jar target/ceylon-0.0.1-SNAPSHOT.jar --spring.profiles.active=uat
```

`./mvnw test` needs network access: `CeylonApplicationTests` is a `@SpringBootTest` that runs Flyway
and Hibernate validation against the **dev database**, which is a shared remote Postgres, not a local
one. There is no Docker on this machine, so Testcontainers-based tests cannot run here yet.

Windows note: the jar is locked while the app runs. Kill the process on port 8080 *before*
`package`, or `repackage` fails with "Unable to rename".

## The docs are the specification

`doc/version 1/` holds the SRS, Technical Architecture, UI Specification and Project Plan. They use
stable requirement ids — `FR-<MODULE>-nn`, `NFR-nn`, `BR-nn`, `IF-nn` — and code comments cite them.
When a decision looks arbitrary, the reason is almost always in there; check before changing it.

`doc/version 1/V1__init.sql` is the **reference schema for the whole product**, not a migration. It
is deliberately not in `db/migration`. Each module's migration takes the tables it owns from that
file, keeping the column names and types so later migrations line up.

`doc/BUILD_PLAN.md` is the living counterpart: which modules exist, what each one owns, the
requirements and acceptance tests it has to satisfy, and what is left. **Read it before starting a
module, and update its status table and checklists when work lands.**

## Architecture

Architecture §4 mandates the layout: each module is a top-level package `lk.ceylonpick.<module>`
exposing a small public API. **Other modules may depend only on that `api` package — never on
another module's entities or repositories.** Cross-module reads go through the API; if that is too
slow, add a read model, not a join.

```
lk/ceylonpick/
  shared/          # the shared kernel: ULID Ids, Hashes, Phones, Clock
    web/           # ApiResponse envelope, ApiException, GlobalExceptionHandler, PageQuery
    i18n/          # MessageResolver — EN/SI/TA
  auth/
    api/           # the only package other modules may import
    domain/ repo/ service/ web/ config/ cache/
```

Hard boundaries from the same section, which matter because they are money rules:
`orders` is the only module that changes order state; `ledger` is the only module that writes ledger
entries (append-only, BR-24); `payments` is the only module that talks to PayHere.

Do not reorganise this into `controller/` + `service/` + `model/` layers. A single `model` package
makes every entity visible to every service and the boundary rules above become unenforceable.

### Auth — the invariant that matters most

The access token is a JWT carrying **only `sub` (the user id)**. Role, admin tier and status are
never claims: they are read live on every request through `AuthUserService`, which is cache-first
(Caffeine, 5-minute TTL) and falls back to the database.

That design only stays correct because **every write to a user's role or status evicts the cache**.
`AccountService` is the single place status is written and it always evicts. Anything that
deactivates people later — creator auto-pause at RTO > 30% (BR-16), vendor suspension on the third
strike (BR-23) — must go through it rather than updating the row directly, or a disabled account
keeps working until the TTL expires. The TTL is a safety net, not the mechanism.

`AuthUserCache` is behind an interface so the in-process implementation can be swapped for Redis
when a second instance appears (ADR-2: no Redis before that).

Logout-everywhere works without an extra claim: `app_user.sessions_invalidated_at` is compared
against the token's standard `iat`.

Refresh tokens are opaque, stored hashed, and rotate on every use. Presenting an already-rotated one
means it leaked, so the whole family is revoked.

### The transaction trap

Two real bugs came from this, so it is worth stating: **a method that ends by throwing rolls back
everything it wrote.** Failed-login counters, lockouts, audit rows and reuse-detection revocations
all have to survive the exception that follows them, so they run in
`@Transactional(propagation = REQUIRES_NEW)` — see `AccountService.recordFailedLogin`, `AuditService`
and `SessionRevoker`.

And that propagation only applies through the proxy. Calling such a method from inside its own class
silently keeps the caller's transaction. `SessionRevoker` exists as a separate bean for exactly this
reason.

### Errors and i18n

Every endpoint returns the `ApiResponse` envelope, success or failure. Throw `ApiException` with a
stable `code`; `GlobalExceptionHandler` looks up `error.<CODE>` in `messages*.properties` and falls
back to the English message on the exception. **Adding a translation is a properties-file change, no
throw site is touched.** `MessageBundleTest` fails the build on a key that exists only in a
translation, or on a blank value.

`messages_ta.properties` has not been reviewed by a native Tamil speaker; NFR-06 makes Tamil a
published-content requirement, so that review is outstanding.

### Migrations

`src/main/resources/db/migration/V1__auth.sql` covers the auth module only. Later modules add their
own numbered migrations. Flyway owns the schema in every environment and `ddl-auto` is `validate`
everywhere — an entity that drifts from the migration fails startup, which is the point.

`otp_challenge.order_id` has no foreign key yet because `"order"` does not exist; the orders
migration must add it.

## Deliberate deviations — do not "fix" these

- **Buyers may hold an email and password.** FR-AUTH-01 says they never do. The customer account is
  an optional upgrade the product owner asked for; guest checkout and phone-OTP sign-in are
  unchanged, and `app_user`'s CHECK constraints permit all three credential shapes.
- **Admins have OWNER and MANAGER tiers.** SRS §2.2 models Admin as one flat role. OWNER covers
  settings, payouts, ledger adjustments and admin accounts; MANAGER covers the daily queues.
- **Staff OTP has its own hourly cap** (`per-staff-phone-per-hour`, default 10). FR-NOT-01's limit of
  3 per phone per hour applied to a mandatory admin second factor locks an admin out of the platform
  for an hour after three logins.
- **`email` is TEXT, not CITEXT** (lower-cased in the application) and **`language` is TEXT + CHECK,
  not CHAR(2)**, to avoid citext JDBC binding friction and bpchar blank-padding.
- The base package is `lk.ceylonpick`, per Architecture §4. `HELP.md` still names the Spring
  Initializr package and is stale.

## Spring Boot 4 differences that have already bitten

- Auto-configuration is split into per-technology modules. `flyway-core` alone leaves Flyway
  **silently unrun** — `spring-boot-flyway` is what wires it up.
- Jackson 3: `tools.jackson.databind.ObjectMapper`. Annotations stay on `com.fasterxml.jackson.annotation`.
- `spring-boot-starter-webmvc`, not `-web`.
- `UserDetailsServiceAutoConfiguration` moved to `org.springframework.boot.security.autoconfigure`.
- Testcontainers 2.x renamed the module to `testcontainers-postgresql`.

## Third parties are stubbed on purpose

PayHere, SMS/WhatsApp, SMTP and R2 are deliberately left until last. Each sits behind an interface
with a stand-in — `OtpSender` logs the code, reset links are logged, the IPN endpoint verifies a
locally computed signature — so the modules that use them are written and tested now, and the real
adapter is dropped in without callers changing.

A stand-in must never weaken a rule it stands in for. The OTP still expires in 10 minutes with 3
attempts; the IPN is still signature-checked and idempotent; the browser return URL still never
confirms an order. See *Deferred integrations* in `doc/BUILD_PLAN.md`.

The web storefront and admin panel are separate repositories consuming this API. Keep it a pure
JSON API: one `ApiResponse` envelope, stable error codes, audience-grouped paths
(`/api/v1/public|vendor|creator|admin/**`). CORS currently runs on defaults, which only holds while
everything is same-origin behind Caddy — before a front end gets its own origin, set the allowed
origins explicitly, because credentialed cookie auth cannot use a wildcard.

## Local development

Profiles are `dev` (default), `uat`, `prod`. uat and prod require `DB_URL`, `DB_USERNAME`,
`DB_PASSWORD` and `JWT_SECRET` from the environment and fail fast without them.

In dev only: OTP codes come back in the API response (`otp.expose-code`), email and reset tokens are
logged rather than sent — no SMS or mail provider is wired up yet — and `auth.bootstrap` seeds the
first OWNER (`owner@ceylonpick.lk`) when no admin exists.

Authentication is cookie-based, so **CSRF applies**: state-changing calls need the `XSRF-TOKEN`
cookie echoed in an `X-XSRF-TOKEN` header. A curl session must hold both or every POST returns 403,
which is easy to mistake for an authorization failure.
