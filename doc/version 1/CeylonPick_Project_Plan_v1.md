# CeylonPick — Final Project Plan (v1.0)
**Curated marketplace for verified Sri Lankan-made brands, sold by creators**
Date: 5 September 2026 | Horizon: 12 months | Funding: LKR 60,000/month (LKR 720,000/year), no profit expected in year 1

---

## සාරාංශය

CeylonPick යනු Daraz copy එකක් නොවේ. එය **verify කළ දේශීය කුඩා brands, creators ලා හරහා විකුණන curated marketplace එකකි.** පළමු අවුරුද්දේ ඉලක්කය ලාභය නොව, **"මේ model එක වැඩ කරනවා" කියලා numbers වලින් ඔප්පු කිරීමයි** — delivered orders, අඩු RTO, order එකකට positive contribution margin, සහ payout ලබන creators ලා. Features ගණන අඩුවෙන්, discipline වැඩියෙන්. දින 90 test එකක්, ලියපු kill criteria, සහ මාසෙට 60,000 සීමාව ඉක්මවා නොයාම මූලික නීති තුනයි.

---

## 1. Vision, Positioning & USP

**Vision:** Sri Lanka's trusted shelf for things made in Sri Lanka — and the only local tool that lets creators earn from recommending them.

**Positioning statement:** *For Sri Lankan buyers who want quality local products from people they can trust, and for creators who want to earn from their audience, CeylonPick is the curated marketplace where every seller is verified and every creator has a storefront.*

**Three pillars of the USP**

| Pillar | What it means in practice | Why incumbents can't copy it |
|---|---|---|
| Verified Sri Lankan Maker | Every vendor physically/video verified; badge on every product | Daraz's scale depends on open onboarding |
| Creator storefronts | ceylonpick.lk/@name — each creator curates and earns 10–20% | No TikTok Shop, Instagram Checkout, or LTK in SL |
| Trust layer | Escrow-style money handling, OTP-confirmed COD, published payout calendar | Facebook sellers can't provide it alone |

**What CeylonPick will never do in year 1:** open/unverified vendor onboarding, low-quality "Daraz-type" inventory, platform-paid shipping, clothing, electronics, news/jobs/general content sections.

---

## 2. Market Snapshot (evidence base)

- Daraz ≈ 46% of SL e-commerce; all other platforms ≈ 16%; the remaining ~38% is un-platformed social selling (Facebook, Instagram, WhatsApp, TikTok). **That 38% is the target supply and demand pool.**
- COD preference 52% (rising); cards 35%; e-wallets ≤1%. COD is a design constraint, not a phase.
- daraz.lk ~1.02M visits/month (flat/declining); AliExpress ~829K from SL — cheap imports are already served. Local, artisan, personalised and natural-beauty goods are not.
- Daraz cut ~30 SL staff in the 2026 regional restructuring; Kapruka needed 23 years and a marketplace pivot to post its first modest full-year profit (FY2026). **Lesson: subsidised scale doesn't work here; asset-light curation does.**
- Sri Lanka has thousands of 5K–200K-follower creators with no clean product-monetisation tool.

---

## 3. Business Model

### 3.1 Revenue

| Stream | Rate | When |
|---|---|---|
| Platform commission on delivered orders | 6% of product price | Day 1 |
| Payment gateway pass-through | Actual PayHere fee (~3%) on prepaid orders, charged to vendor | Day 1 |
| Signature collection placement fee | Flat LKR 2,500/month per vendor for top-tier curated shelf | Month 7+ |
| Vendor-paid campaign boosts (creator seeding funded by vendor) | 10% coordination fee on seeding budget | Month 6+ |
| Corporate gifting (B2B hampers) | 8% commission + handling | Month 6+ |

### 3.2 Commission structure (vendor-funded affiliate model)

Product price LKR 3,000 example:

| Party | Share | LKR |
|---|---|---|
| Creator (affiliate) | 15% (vendor sets 10–20%) | 450 |
| CeylonPick platform | 6% | 180 |
| PayHere fee (if prepaid) | ~3% | 90 |
| Courier (paid by buyer at checkout) | — | 300–400 |
| Vendor receives | ~76% of price | 2,280 |

Vendors in these niches run 50–70% gross margins; this structure leaves them profitable and replaces money they already burn on boosted posts.

### 3.3 Unit economics target (per delivered order, month 12)

| Metric | Target |
|---|---|
| Average order value | LKR 2,800–3,500 |
| Platform commission | LKR 170–210 |
| Allocated RTO cost (at 15% RTO, courier return LKR 250) | ~LKR 45 |
| Allocated tools/hosting per order (at 700 orders/month) | ~LKR 10 |
| **Contribution margin per delivered order** | **LKR 115–155 (positive)** |

---

## 4. Niche Roadmap

| Phase | Months | Niche | Payment rule | Why this order |
|---|---|---|---|---|
| 1 | 1–3 | Artisan consumables & gifting hampers (tea, spices, kithul, honey, natural soaps, snacks) | COD ≤ LKR 5,000 | Light, low RTO, giftable (often prepaid), strong "SL-made" story |
| 2 | 4–6 | Personalised & made-to-order gifts | **Prepaid only** | Personalisation justifies prepayment; pushes COD share down |
| 3 | 7–12 | Local natural beauty & wellness micro-brands | COD ≤ LKR 3,000 | Largest creator pool; strict COD controls needed |

Each expansion requires **300 delivered orders** in the current scope. Geography: Colombo, Gampaha, Kalutara for months 1–3; island-wide from month 4 only if RTO < 20%.

---

## 5. Operations & Core Flows

### 5.1 Order flow
1. Buyer orders (web, mobile-first). Prepaid via PayHere or COD.
2. COD → automatic OTP/WhatsApp confirmation. No confirmation in 24h → auto-cancel (affiliate sees "unconfirmed", not "sale").
3. Vendor notified → packs → uploads packing photo (dispute protection) → marks ready.
4. Courier pickup (Koombiyo / Prompt Xpress / Pronto) → tracking ID stored → status via courier updates.
5. Delivered → 7-day hold → vendor payout and creator commission accrue.
6. RTO → parcel returns to vendor; return courier fee charged to vendor (or split, agreed in vendor T&Cs); no commission.

### 5.2 Money flow (escrow discipline)
All inbound money (PayHere settlements + courier COD remittance) lands in the CeylonPick business account and is treated as **held funds**, split in the ledger into: Vendor payable / Creator payable / Platform revenue / Gateway fee. Payouts: vendors weekly (delivered + 7), creators twice monthly (min LKR 2,000), on a **published calendar**.

### 5.3 Vendor onboarding
Apply → NIC/BR + product samples → 20-minute video call → quality check against written standard → "Verified Sri Lankan Maker" badge → 10–15 SKUs listed (CeylonPick shoots photos) → commission agreement signed. Cap: 25 vendors in phase 1, 50 by month 6, 100 by month 12.

### 5.4 Creator onboarding (marketers' entry point)
"Earn with CeylonPick" page → apply (handle, niche, follower range) → approval → storefront page + unique links/codes → Campaign Board access → product seeding → dashboard (clicks, confirmed orders, delivered, RTO, payable). Auto-pause at RTO > 30%; removal at > 40%.

### 5.5 Campaign Board (the "marketers page", done right)
Vendors post offers: product, commission %, samples available, campaign window (e.g., Avurudu). Creators apply, receive samples, publish, track. Months 1–3: Google Form + WhatsApp group. Month 4–6: built into the platform.

### 5.6 Returns & disputes
Damaged/wrong-item only. Photo evidence within 48h of delivery. Founder handles every dispute personally for 6 months. Vendor packing photo is the arbiter.

### 5.7 Weekly management rhythm
- **Mon:** four KPIs reviewed (delivered orders, RTO %, contribution margin/order, creator payouts).
- **Wed:** vendor check-ins; creator group brief (one product, three hook ideas).
- **Fri:** payout run; courier remittance reconciliation.
- **Monthly:** continue/kill review against written criteria.

---

## 6. Marketing Plan (12 months)

### 6.1 Principle
CeylonPick does not run ads; it equips 20–100 creators to sell. Paid media exists only to amplify creator content that has already converted organically.

### 6.2 Channels by phase

| Phase | Creators | Channel actions | Paid budget |
|---|---|---|---|
| Setup (M1) | Recruit 20 founding creators (5K–50K followers; Sinhala/Tamil lifestyle, food, home, student) | WhatsApp founders' group; brand kit; seeding | 0 |
| Test (M2–3) | 20 active | 1 brief/week; buyer WhatsApp broadcast list; thank-you card with creator code | 0 |
| Fix (M4–6) | 40–50 | Campaign Board live; UGC voucher (LKR 300 for unboxing video); first boosts on top creator videos | LKR 5–8K/month |
| Scale (M7–12) | 100+ | Signature collection launch; corporate gifting outreach (HR/admin pages, 50 companies); creator leaderboard with monthly bonus | LKR 6–10K/month |

### 6.3 Occasion calendar (content anchors)
Valentine's (Feb) · Sinhala & Tamil New Year (Apr, the year's biggest gifting window) · Vesak/Poson (May/Jun) · A/L results & university intake (varies) · Mother's/Father's Day · Christmas & year-end corporate gifting (Nov–Dec). Every occasion gets a curated landing page and a creator brief 4 weeks ahead.

### 6.4 Retention
WhatsApp broadcast list (opt-in on every order) → occasion reminders and creator picks. Buyer referral: refer a friend → LKR 300 off for both after the friend's first delivered order. Target repeat-purchase rate ≥ 15% by month 6, ≥ 25% by month 12.

### 6.5 Trust marketing (zero-cost)
Founder's face and name on the site. Public "creator payout log" (aggregate, updated fortnightly). Vendor stories (short video per vendor). First five paid creators asked to post confirmation.

### 6.6 Additional ideas worth testing (my additions)
- **Corporate gifting B2B** — hampers for staff/clients; higher AOV, always prepaid, seasonal. Start with 50 cold outreaches in month 5.
- **Creator "drops"** — a creator co-curates a limited hamper with a vendor; scarcity + creator ownership drives conversion.
- **Vendor RTO protection fee** — optional LKR 50/order fee that caps a vendor's return-shipping exposure; small revenue, big vendor comfort.
- **Diaspora channel (year 2)** — Kapruka's cross-border unit shows USD demand exists; requires international payments (PayPal arrival helps) and export packaging. Not in year 1.

---

## 7. Financial Plan (LKR 720,000)

### 7.1 Allocation by phase

| Phase | Months | Budget | Main spend |
|---|---|---|---|
| Setup | 1 | 65,000 | Registration, hosting/domain/tools, photography, first seeding |
| Test | 2–3 | 120,000 | Seeding, content, RTO reserve |
| Fix & niche 2 | 4–6 | 180,000 | Seeding, Campaign Board build, first boosts, part-time ops |
| Scale | 7–12 | 355,000 | Seeding at 100 creators, Signature launch, ops help, boosts |
| **Total** | | **720,000** | |

### 7.2 Steady-state monthly budget (LKR 60,000)

| Item | LKR |
|---|---|
| Creator seeding & micro-incentives | 18,000 |
| Content (photos, reels) | 8,000 |
| Hosting, tools, gateway tier | 6,000 |
| Paid boosts (creator content only, M4+) | 6,000 |
| RTO/dispute reserve (rolls over if unused) | 8,000 |
| Packaging, cards, tape | 4,000 |
| Part-time ops (M4+) | 10,000 |
| **Total** | **60,000** |

Founder salary: 0 (recorded as contributed effort). Courier: always paid by buyer/vendor. Reserve rule: never let the account fall below two months of funding (LKR 120,000).

### 7.3 Accounting rules
1. Separate business bank account; monthly LKR 60,000 recorded as capital contribution.
2. Ledger with held funds split (vendor payable / creator payable / platform revenue / gateway fee). Platform revenue is the only revenue line.
3. Weekly reconciliation of courier remittance vs orders.
4. Monthly P&L: platform revenue − operating costs = burn. Target burn → near 0 by month 12.
5. Every invoice and courier bill filed from day 1 (tax readiness).

---

## 8. KPIs, Milestones & Kill Criteria

| Checkpoint | Delivered orders/month | RTO | Active paid creators | Repeat rate | Contribution margin/order |
|---|---|---|---|---|---|
| Day 90 (kill gate) | ≥ 150 | ≤ 25% | ≥ 8 | ≥ 10% | ≥ 0 by week 8 |
| Month 6 | ≥ 350 | ≤ 20% | ≥ 30 | ≥ 15% | ≥ LKR 80 |
| Month 12 | ≥ 700 | ≤ 15% | ≥ 80 | ≥ 25% | ≥ LKR 115 |

**Kill gate rule:** if any day-90 metric fails, stop spending on growth, diagnose for 30 days with ≤ LKR 30,000, then either pivot (different niche/geography) or wind down. Decision is made against these numbers, not feelings.

---

## 9. Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Empty marketplace (vendors/creators churn before demand) | High | Fatal | 20 seeded creators + 20 met vendors before launch; one niche |
| COD RTO > 25% | High | Severe | OTP confirmation, COD caps, prepaid personalisation, Western Province first |
| Affiliate fraud (fake COD orders) | Medium | Severe | Pay on delivered+7; RTO auto-pause; T&C forfeiture |
| Late payouts damage trust | Medium | Fatal to supply side | Published calendar; reserve fund; founder-run payout day |
| Founder over-builds software | High | Severe | v1 scope frozen (Section 10); 8-week build timebox |
| Vendor quality scandal | Low | Fatal to USP | In-person/video verification; packing photos; fast delisting |
| Courier remittance delays | Medium | Cash-flow | Two courier partners; weekly reconciliation |
| Founder burnout (solo) | Medium | Severe | Part-time ops from month 4; weekly rhythm; kill criteria remove agonising |

---

## 10. Product Scope — v1 (frozen)

**In v1 (months 1–3):**
1. Home — curated, one niche, verified badges, founder note
2. Category pages (Gifting; Personalised and Beauty added by phase)
3. Product page — photos, vendor story, badge, creator attribution
4. Checkout — PayHere + COD with OTP confirmation, buyer-paid shipping
5. Creator storefront pages (ceylonpick.lk/@name) + unique links/codes
6. "Earn with CeylonPick" landing page + application
7. "Sell with us" vendor page + application
8. Admin: orders, order state, payouts ledger export
9. WhatsApp order/status notifications

**v2 (months 4–6):** Campaign Board in-platform; creator dashboard (clicks/orders/RTO/payable); buyer reviews; buyer referral codes.
**v3 (months 7–12):** Signature collection; occasion landing pages; vendor self-onboarding (still verified); blog with gift guides and vendor stories; corporate gifting request form.

**Not in the roadmap:** news section, general jobs board, clothing, electronics, low-quality inventory, native mobile app (year 1), microservices.

---

## 11. Technology Direction (summary; detail agreed separately)

Custom build is acceptable **only** because the founder is a developer using AI-assisted tooling and can hold to an 8-week v1 timebox. Stack: Next.js (mobile-first PWA) + Spring Boot **modular monolith** + PostgreSQL, deployed on a single low-cost VPS or managed PaaS. No Redis, no microservices, no native app in year 1. Order status as an explicit state machine; money as a double-entry ledger; idempotent webhooks for PayHere and courier callbacks. Hard rule: if v1 is not live by week 8, switch to WooCommerce and ship anyway.

---

## 12. 12-Month Timeline

| Month | Milestone |
|---|---|
| 1 | Registered, PayHere live, 20 vendors verified, 20 creators seeded, v1 site live (week 8 at latest) |
| 2 | Public launch, Western Province, niche 1 |
| 3 | **Day-90 kill gate review** |
| 4 | Niche 2 (personalised, prepaid), Campaign Board (manual), part-time ops |
| 5 | Corporate gifting outreach begins; first boosts |
| 6 | Month-6 review; 50 vendors; creator dashboard live |
| 7 | Niche 3 (beauty); island-wide if RTO < 20% |
| 8–9 | Signature collection; occasion landing pages; blog |
| 10–11 | Year-end gifting season push (biggest revenue window) |
| 12 | Year-1 review; investor/loan data pack; year-2 plan (diaspora, app decision) |

---

*Prepared as the working baseline for CeylonPick. Every number above is a target or an estimate to be replaced by real data from week 1 onward.*
