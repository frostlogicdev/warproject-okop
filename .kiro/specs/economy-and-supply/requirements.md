# Requirements Document — Economy & Supply

## Introduction

This document specifies the economy and supply subsystem for the War Project Minecraft
mod (NeoForge 1.21.1, Java 21). It introduces per-player wallets, faction treasuries,
salaries, taxes, a quartermaster shop, periodic supply drops, and a black market. All
amounts are non-negative integers ("WP credits"). The subsystem is the foundation that
later modules (operations, hierarchy, bases, civilian roles) depend on for cost,
reward, and incentive structure.

## Glossary

- **Wallet**: per-player non-negative integer balance of WP credits.
- **Treasury**: per-faction non-negative integer balance of WP credits.
- **Wallet_Service**: server-side service managing player wallets and transaction logs.
- **Treasury_Service**: server-side service managing faction treasuries.
- **Shop_Service**: server-side service managing shop items, prices, and orders.
- **Quartermaster**: NPC that exposes a shop GUI and price catalog. Lives at faction bases.
- **Black_Market**: special shop in a neutral zone with elevated prices and audit flags.
- **Supply_Drop**: scheduled crate that spawns at a faction base, scaling loot to
  faction state.
- **Salary**: amount credited to a player's wallet at periodic intervals while online
  and in `ACCEPTED` status.
- **Tax**: percentage deducted from each salary payment and credited to the treasury.
- **Faction_Member**: a player in `ACCEPTED` state with an assigned `FactionId`.
- **General**: a player holding the `GENERAL` role within a faction.
- **Minister**: a player holding the `MINISTER_OF_DEFENSE` or `FOREIGN_MINISTER` position
  (introduced in `hierarchy-and-state`; pre-positions release uses `GENERAL`).

## Requirements

### Requirement 1: Wallet creation and balance

**User Story:** As a faction member, I want a personal wallet, so that I can hold
WP credits earned from salaries and missions.

#### Acceptance Criteria

1. WHEN a player first reaches `ACCEPTED` state in any faction, THE Wallet_Service
   SHALL create a wallet row with balance = `INITIAL_BALANCE` (default 100) and
   persist it.
2. THE Wallet_Service SHALL reject any debit that would leave the balance below 0
   with `Result.failure("wp.economy.wallet.insufficient")`.
3. THE Wallet_Service SHALL reject any credit/debit whose absolute value exceeds
   `MAX_TX_AMOUNT` (default 1_000_000) with `Result.failure("wp.economy.wallet.amount_overflow")`.
4. WHEN a player executes `/wp money`, THE Wallet_Service SHALL display the player's
   current balance in chat, formatted as "Баланс: <amount> кр.".
5. WHEN a player executes `/wp money <player>`, THE Wallet_Service SHALL display the
   target player's wallet balance only if the executor has `OP` permission level ≥ 2.
6. IF a player without sufficient permission executes `/wp money <player>`, THEN THE
   Wallet_Service SHALL respond with `wp.economy.no_permission`.

### Requirement 2: Wallet transactions

**User Story:** As a faction member, I want to transfer credits to other players,
so that I can pay debts, buy items, or fund subordinates.

#### Acceptance Criteria

1. WHEN a player executes `/wp money pay <player> <amount>` with `1 ≤ amount ≤ MAX_TX_AMOUNT`,
   THE Wallet_Service SHALL atomically debit the sender, credit the receiver, and
   write a `wallet_tx` row with type `TRANSFER`.
2. IF the sender's balance is less than `amount`, THEN THE Wallet_Service SHALL
   reject the transfer with `wp.economy.wallet.insufficient` and SHALL NOT write a
   transaction row.
3. IF the target player has no wallet (never reached ACCEPTED), THEN THE Wallet_Service
   SHALL reject the transfer with `wp.economy.wallet.target_not_found`.
4. THE Wallet_Service SHALL persist every credit and debit as an immutable
   `wallet_tx` row containing: `id`, `wallet_uuid`, `amount` (signed), `type`,
   `counterparty_uuid` (nullable), `reason_key`, `created_at`, `extra_json` (nullable).
5. WHEN a player executes `/wp money history` with no arguments, THE Wallet_Service
   SHALL return the player's most recent 10 transactions ordered by `created_at` DESC.
6. WHEN a player executes `/wp money history <n>` with `1 ≤ n ≤ 100`, THE
   Wallet_Service SHALL return the player's most recent n transactions.
7. THE Wallet_Service SHALL log every successful transaction via `AuditLogger` with
   action `WALLET_TX` and `extra_json` containing `{type, amount, counterparty}`.
   The audit row SHALL NOT contain free-form notes.

### Requirement 3: Treasury

**User Story:** As a general or minister, I want to manage the faction treasury, so
that I can fund operations and pay collective expenses.

#### Acceptance Criteria

1. WHEN the server starts and a faction has no treasury row, THE Treasury_Service
   SHALL insert a treasury row with balance = `TREASURY_INITIAL_BALANCE` (default 5000).
2. WHEN a General executes `/wp treasury`, THE Treasury_Service SHALL display the
   faction treasury balance, formatted as "Казна <faction>: <amount> кр.".
3. WHEN a General executes `/wp treasury pay <player> <amount>`, THE Treasury_Service
   SHALL atomically debit the treasury, credit the player wallet, and write a
   `treasury_tx` row with type `DISBURSEMENT`. The target player MUST belong to the
   same faction; cross-faction disbursement is rejected with `wp.economy.treasury.cross_faction`.
4. WHEN any wallet is debited via `tax`, `purchase fee`, `fine`, or `treasury
   disbursement reversal`, THE Treasury_Service SHALL increment treasury balance by
   the corresponding amount within the same transaction as the wallet write.
5. THE Treasury_Service SHALL reject any debit that would leave treasury balance
   below 0 with `wp.economy.treasury.insufficient`.
6. WHEN a General executes `/wp treasury history <n>` with `1 ≤ n ≤ 100`, THE
   Treasury_Service SHALL return the most recent n treasury transactions.
7. IF a player without `GENERAL` role or higher executes any `/wp treasury` subcommand
   other than balance display, THEN THE Treasury_Service SHALL reject with
   `wp.economy.no_permission`.

### Requirement 4: Salary

**User Story:** As a faction member, I want to receive a periodic salary based on my
rank, so that staying active is rewarded.

#### Acceptance Criteria

1. THE Wallet_Service SHALL credit each `ACCEPTED` faction member's wallet every
   `SALARY_INTERVAL_MIN` minutes of online time (default 30 minutes) with the amount
   defined in `SALARY_BY_ROLE` for the player's current role (defaults: `SOLDIER=50`,
   `COMMANDER=100`, `GENERAL=200`, `CANDIDATE=0`).
2. IF the faction treasury balance is less than the salary amount, THEN THE
   Wallet_Service SHALL skip the payment for that player, log an audit entry with
   action `SALARY_SKIP`, and SHALL NOT decrement the treasury below 0.
3. THE salary payment SHALL be a single atomic operation: treasury debit +
   wallet credit + `wallet_tx` row with type `SALARY` + `treasury_tx` row with type
   `SALARY_PAYMENT`.
4. THE Wallet_Service SHALL track per-player accumulated online minutes in memory
   and reset the counter to 0 immediately after each salary payment.
5. WHEN a player disconnects, THE Wallet_Service SHALL persist the player's
   accumulated online minutes to the `wallets` row so the counter survives restart.
6. IF a player's role changes mid-interval, THEN THE Wallet_Service SHALL prorate
   the next salary by computing weighted average across the interval, rounded down to
   the nearest integer.

### Requirement 5: Tax

**User Story:** As a general, I want to set a faction-wide tax on salaries, so that
the treasury accumulates funds proportional to faction activity.

#### Acceptance Criteria

1. WHEN a General executes `/wp treasury tax <percent>` with `0 ≤ percent ≤ 25`, THE
   Treasury_Service SHALL update the `tax_policy` row for the faction to that percent.
2. IF a General executes `/wp treasury tax <percent>` with percent outside `[0,25]`,
   THEN THE Treasury_Service SHALL reject with `wp.economy.tax.out_of_range`.
3. AT salary payment time, THE Wallet_Service SHALL deduct `floor(salary * tax/100)`
   from the salary, credit it to the treasury within the same transaction, and write
   the deducted amount in the `wallet_tx.extra_json` field as `{tax: <amount>}`.
4. THE Treasury_Service SHALL audit-log every tax-rate change with action
   `TAX_RATE_CHANGE` and `extra_json` containing `{old, new}`.
5. THE default tax rate SHALL be 10 percent.

### Requirement 6: Quartermaster shop

**User Story:** As a faction member, I want to buy gear from the faction quartermaster,
so that I can equip myself for missions.

#### Acceptance Criteria

1. WHEN a player right-clicks a Quartermaster NPC, THE Shop_Service SHALL open a
   shop GUI listing all `shop_items` rows with `faction = <player faction>` AND
   `enabled = 1`, paginated 9 items per page.
2. EACH shop item SHALL have: `id`, `faction`, `item_id` (Minecraft resource location),
   `count`, `price`, `min_role`, `enabled`, `category`.
3. WHEN a player clicks an item with sufficient wallet balance AND meeting `min_role`,
   THE Shop_Service SHALL atomically debit the wallet, credit the treasury, and give
   the item stack to the player. Excess stack overflow is dropped at the player's feet.
4. IF a player attempts to buy an item whose `min_role` is higher than the player's
   role, THEN THE Shop_Service SHALL reject with `wp.economy.shop.role_required`.
5. IF wallet balance is insufficient, THEN THE Shop_Service SHALL reject with
   `wp.economy.wallet.insufficient` and close the GUI for that item only.
6. EACH purchase SHALL produce: `wallet_tx` (type `PURCHASE`, negative amount),
   `treasury_tx` (type `PURCHASE_REVENUE`, positive amount), and `shop_orders` row
   (id, player_uuid, item_id, count, price_paid, created_at).
7. WHEN a Minister executes `/wp shop set <item_id> <count> <price> <min_role>`, THE
   Shop_Service SHALL upsert the corresponding `shop_items` row for the Minister's faction.
8. WHEN a Minister executes `/wp shop disable <id>` or `/wp shop enable <id>`, THE
   Shop_Service SHALL set `enabled` accordingly.

### Requirement 7: Black market

**User Story:** As a player, I want to access a black market with rare items, so that
I have an off-faction option at higher cost and higher risk.

#### Acceptance Criteria

1. THE Black_Market SHALL be a Quartermaster NPC variant with `faction = NEUTRAL`,
   spawned only at coordinates listed in `WpConfig.ECONOMY_BLACK_MARKET_LOCATIONS`.
2. THE Black_Market SHALL apply a price multiplier of `BLACK_MARKET_MULTIPLIER`
   (default 2.5) to all listed item prices.
3. WHEN a Faction_Member purchases from the Black_Market, THE Shop_Service SHALL
   audit-log the purchase with action `BLACK_MARKET_BUY`, increment the player's
   `casus_belli_incidents` counter (forwarded to `hierarchy-and-state` when active),
   and SHALL NOT credit any treasury.
4. THE Black_Market SHALL not honor `min_role` restrictions; any player can buy.
5. THE Black_Market SHALL support a separate item catalog stored in `shop_items` with
   `faction = NEUTRAL`.

### Requirement 8: Supply drops

**User Story:** As a faction, we want periodic supply drops at our base, so that
content keeps rotating even without staff intervention.

#### Acceptance Criteria

1. THE Shop_Service SHALL spawn one Supply_Drop per faction every
   `SUPPLY_DROP_INTERVAL_MIN` minutes (default 120 minutes), placed at the faction's
   spawn coordinates plus a random offset within ±16 blocks on the X/Z plane.
2. EACH Supply_Drop SHALL be a marked supply_crate block with a `WpDataAttachment`
   indicating the drop tier, owner faction, and expiration timestamp
   (`now + DROP_EXPIRY_MIN` minutes, default 60).
3. EACH Supply_Drop SHALL contain a randomly weighted selection of items defined in
   `WpConfig.ECONOMY_DROP_TABLES` for that drop tier.
4. THE drop tier SHALL be derived from the faction's current war state (peace=1,
   tension=2, war=3) once the `hierarchy-and-state` module is active. Until then, the
   tier SHALL be fixed at `SUPPLY_DROP_DEFAULT_TIER` (default 2).
5. WHEN a player from a different faction opens a Supply_Drop, THE Shop_Service SHALL
   allow it but SHALL audit-log with action `SUPPLY_DROP_LOOTED_BY_ENEMY` and post a
   chat broadcast to the owning faction: `wp.economy.supply.looted`.
6. IF a Supply_Drop has not been opened by `expiration timestamp`, THEN THE
   Shop_Service SHALL despawn it on the next supply tick and audit-log with
   `SUPPLY_DROP_EXPIRED`.

### Requirement 9: Persistence and migration

**User Story:** As a server operator, I want all economy data persisted reliably, so
that wallets, treasuries, and shop state survive restart.

#### Acceptance Criteria

1. THE economy module SHALL ship migration `V3__economy_and_supply.sql` adding tables
   `wallets`, `wallet_tx`, `treasury`, `treasury_tx`, `shop_items`, `shop_orders`,
   `tax_policy` with the columns and constraints defined in `design.md`.
2. ALL DAOs (`WalletsDao`, `TreasuryDao`, `ShopItemsDao`, `ShopOrdersDao`) SHALL
   accept a `Connection` parameter so callers can compose multi-table operations
   inside a single `Database.inTx`.
3. EVERY wallet write, treasury write, and order insert SHALL run inside an
   `inTx` block; partial writes SHALL never be possible.
4. THE migration SHALL register in `index.txt` directly after `V2__military_features.sql`.

### Requirement 10: Configuration

**User Story:** As a server operator, I want every economy parameter to be config-driven,
so that I can tune the experience without rebuilding.

#### Acceptance Criteria

1. THE economy module SHALL add a `WpConfig` section `economy` with at least:
   `initialBalance`, `treasuryInitialBalance`, `maxTxAmount`, `salaryIntervalMin`,
   `salaryByRole` (string list), `defaultTaxPercent`, `blackMarketMultiplier`,
   `blackMarketLocations` (string list), `supplyDropIntervalMin`, `supplyDropDefaultTier`,
   `supplyDropExpiryMin`.
2. EACH integer parameter SHALL use `defineInRange` with sensible bounds.
3. THE config SHALL be hot-reloadable via `/wp reload`; runtime caches SHALL refresh
   on `ModConfigEvent.Reloading`.

## Out of scope

- Cross-server economy or external currency exchange.
- Real-money integration.
- Stock market / commodity pricing models.
- Detailed inventory weighting (covered separately by `logistics-and-supply-chain`).
