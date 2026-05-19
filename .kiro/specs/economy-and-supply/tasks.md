# Implementation Plan — Economy & Supply

## Overview

Implements the economic backbone for the War Project. Tasks are ordered to layer
foundations first (DB schema, config, DAOs), then services, then commands and
NPC integrations, finishing with supply drops and tests.

## Tasks

- [ ] 1. Database migration and config
  - [ ] 1.1 Write `V3__economy_and_supply.sql` with all 7 tables and indexes from `design.md`.
    Register it in `index.txt` after V2.
    _Requirements: 9.1, 9.4_
  - [ ] 1.2 Add `economy` config section to `WpConfig` with all 12 parameters listed in `design.md`.
    _Requirements: 10.1, 10.2_

- [ ] 2. DAOs
  - [ ] 2.1 `WalletsDao` — get/upsert/balance update, online-minute persistence.
    _Requirements: 1.1, 4.5, 9.2_
  - [ ] 2.2 `WalletTxDao` — insert + paged query by wallet_uuid.
    _Requirements: 2.4, 2.5, 2.6_
  - [ ] 2.3 `TreasuryDao` and `TreasuryTxDao` — analogous to wallet pair.
    _Requirements: 3.1, 3.6, 9.2_
  - [ ] 2.4 `TaxPolicyDao` — get/upsert tax rate per faction.
    _Requirements: 5.1, 5.4_
  - [ ] 2.5 `ShopItemsDao` — CRUD with unique (faction, item_id, category) constraint.
    _Requirements: 6.2, 6.7, 6.8_
  - [ ] 2.6 `ShopOrdersDao` — insert + recent-by-player query.
    _Requirements: 6.6_
  - [ ] *2.7 DAO round-trip property tests on H2.

- [ ] 3. Services
  - [ ] 3.1 `WalletService` with all public methods from `design.md`. All multi-step
    operations wrapped in `database.inTx`. Audit-logs on every write.
    _Requirements: 1.1–1.6, 2.1–2.7_
  - [ ] 3.2 `TreasuryService` analogous; tax rate setter with audit.
    _Requirements: 3.1–3.7, 5.1–5.5_
  - [ ] 3.3 `ShopService` with `purchase`, `upsertItem`, `setEnabled`, `listItems`.
    Black-market multiplier handled via `ShopMode` flag.
    _Requirements: 6.1–6.8, 7.1–7.5_
  - [ ] 3.4 `SupplyDropService` with tick-driven scheduling and expiry sweep.
    _Requirements: 8.1–8.6_

- [ ] 4. Tick handlers
  - [ ] 4.1 `SalaryTickHandler` registered on `ServerTickEvent.Post`. Accumulates
    per-player online minutes and pays salaries with tax deduction.
    _Requirements: 4.1–4.6, 5.3_
  - [ ] 4.2 Hook `WalletService.onPlayerLogin/Logout` from `WarProjectServerEvents`.
    _Requirements: 4.4, 4.5_

- [ ] 5. Commands
  - [ ] 5.1 `EconomyCommands` with `/wp money` (balance, pay, history),
    `/wp treasury` (balance, pay, tax, history), `/wp shop` (set, enable, disable, list).
    Use `Result<T>` for output.
    _Requirements: 1.4–1.6, 2.1, 2.5, 3.2, 3.3, 3.6, 5.1, 6.7, 6.8_
  - [ ] 5.2 Register node tree under `/wp` root in `WpCommandRoot`.

- [ ] 6. NPCs
  - [ ] 6.1 Quartermaster NPC variant of `FactionNpcEntity`. Right-click opens shop GUI.
    _Requirements: 6.1_
  - [ ] 6.2 Black-marketeer NPC: `faction = NEUTRAL`, spawns at configured locations.
    _Requirements: 7.1, 7.5_

- [ ] 7. Network and client
  - [ ] 7.1 `OpenShopPayload`, `ShopCatalogPayload`, `ShopBuyPayload`,
    `WalletSnapshotPayload`. Register via `WpPayloadRegistrar`.
  - [ ] 7.2 `ShopScreen` (client) — paginated 9 items per page, shows price/role/multiplier
    badge for black market. Sends `ShopBuyPayload` on click.
    _Requirements: 6.1, 7.2_
  - [ ] 7.3 Optional `WalletHud` element rendering current balance in HUD when player
    has the `economy/wallet_visible` config flag enabled.

- [ ] 8. Integration with existing systems
  - [ ] 8.1 In `ServiceRegistry` add lazy accessors for all four economy services.
  - [ ] 8.2 In acceptance flow (faction acceptance) call `WalletService.ensureWallet`.
    _Requirements: 1.1_
  - [ ] 8.3 Add `WALLET_TX`, `TREASURY_TX`, `TAX_RATE_CHANGE`, `SALARY_SKIP`,
    `BLACK_MARKET_BUY`, `SUPPLY_DROP_EXPIRED`, `SUPPLY_DROP_LOOTED_BY_ENEMY`
    to `AuditAction`.
  - [ ] 8.4 i18n keys in both `en_us.json` and `ru_ru.json` for all command outputs,
    error messages, GUI labels.

- [ ] 9. Property tests *(optional but recommended)*
  - [ ] *9.1 Property 1 — wallet non-negative invariant.
  - [ ] *9.2 Property 2 — wallet sum = balance.
  - [ ] *9.3 Property 3 — treasury non-negative invariant.
  - [ ] *9.4 Property 4 — tax math.
  - [ ] *9.5 Property 5 — transfer atomicity.
  - [ ] *9.6 Property 6 — purchase row count.
  - [ ] *9.7 Property 7 — purchase rollback.
  - [ ] *9.8 Property 8 — black-market price.
  - [ ] *9.9 Property 9 — salary skip on broke treasury.
  - [ ] *9.10 Property 10 — online minutes persistence.

- [ ] 10. Checkpoint — Final tests pass
  - Verify `gradlew test` and `gradlew runClient` (manual smoke) succeed before
    declaring the module DRAFT-FULL → IMPLEMENTED.

## Notes

- Tasks marked `*` are optional but strongly recommended for confidence.
- Each task references requirements for traceability.
- The module unblocks operations (3), logistics (5), bases (12), civilian roles (14)
  and any other module that needs cost or reward signals.
