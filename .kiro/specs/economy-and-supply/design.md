# Design Document — Economy & Supply

## Overview

This module introduces the economic backbone for the War Project mod: per-player
wallets, per-faction treasuries, salaries, taxes, a quartermaster shop GUI, supply
drops, and a black market. It is the foundation for cost/reward signaling in every
later module.

All persistence uses the existing `Database` JDBC manager. All command nodes plug
into `WpCommandRoot`. All payloads use the existing `WpPayloadRegistrar`.

## Architecture

```mermaid
graph TD
    subgraph "Server"
        WS[WalletService]
        TS[TreasuryService]
        SS[ShopService]
        SDS[SupplyDropService]
        SAL[SalaryTickHandler]
        BM[BlackMarketHandler]
        DB[(Database)]
    end
    subgraph "Client"
        SHOP_GUI[ShopScreen]
        WALLET_HUD[WalletHud (optional)]
    end
    SAL --> WS
    SAL --> TS
    WS --> DB
    TS --> DB
    SS --> WS
    SS --> TS
    SS --> DB
    SDS --> DB
    BM --> SS
    SHOP_GUI -- buy --> SS
```

## Components

### `WalletService`

```java
public final class WalletService {
    private final Database database;
    private final WalletsDao walletsDao;
    private final WalletTxDao walletTxDao;
    private final TreasuryService treasuryService;
    private final AuditLogDao auditLogDao;

    public Result<Long> getBalance(UUID playerUuid);
    public Result<Void> ensureWallet(UUID playerUuid, FactionId faction);
    public Result<Void> credit(UUID playerUuid, long amount, TxType type, @Nullable UUID counterparty, String reasonKey);
    public Result<Void> debit(UUID playerUuid, long amount, TxType type, @Nullable UUID counterparty, String reasonKey);
    public Result<Void> transfer(UUID from, UUID to, long amount, String reasonKey);
    public List<WalletTx> recentTransactions(UUID playerUuid, int limit);
    public void onPlayerLogin(ServerPlayer player);   // restores online-minute counter
    public void onPlayerLogout(ServerPlayer player);  // persists counter
}
```

### `TreasuryService`

```java
public final class TreasuryService {
    private final Database database;
    private final TreasuryDao treasuryDao;
    private final TreasuryTxDao treasuryTxDao;
    private final TaxPolicyDao taxPolicyDao;
    private final AuditLogDao auditLogDao;

    public Result<Long> getBalance(FactionId faction);
    public Result<Void> credit(FactionId faction, long amount, TreasuryTxType type, @Nullable UUID counterparty, String reasonKey);
    public Result<Void> debit(FactionId faction, long amount, TreasuryTxType type, @Nullable UUID counterparty, String reasonKey);
    public Result<Void> setTaxRate(FactionId faction, int percent, UUID actor);
    public int getTaxRate(FactionId faction);
    public List<TreasuryTx> recentTransactions(FactionId faction, int limit);
}
```

### `ShopService`

```java
public final class ShopService {
    private final Database database;
    private final ShopItemsDao shopItemsDao;
    private final ShopOrdersDao shopOrdersDao;
    private final WalletService walletService;
    private final TreasuryService treasuryService;
    private final AuditLogDao auditLogDao;

    public List<ShopItem> listItems(FactionId faction);
    public Result<Void> upsertItem(FactionId faction, String itemId, int count, long price, Role minRole, String category, UUID actor);
    public Result<Void> setEnabled(int shopItemId, boolean enabled, UUID actor);
    public Result<Void> purchase(ServerPlayer buyer, int shopItemId);   // also handles black market
}
```

### `SupplyDropService`

```java
public final class SupplyDropService {
    private final Database database;
    private final ShopItemsDao shopItemsDao;

    public void onServerTick(ServerTickEvent.Post event);  // fires every config interval
    private void spawnDrop(FactionId faction, MinecraftServer server);
    private void expireOverdueDrops(MinecraftServer server);
}
```

### `SalaryTickHandler`

```java
public final class SalaryTickHandler {
    private final WalletService walletService;
    private final TreasuryService treasuryService;
    private final AuditLogDao auditLogDao;

    public void onServerTick(ServerTickEvent.Post event);  // accumulates online minutes per player
    private void payIfDue(ServerPlayer player);
}
```

### `BlackMarketHandler`

Subclass of `FactionNpcEntity` (or `WpNpcRole.BLACK_MARKETEER` if module 9 is in
scope). Spawned at `WpConfig.ECONOMY_BLACK_MARKET_LOCATIONS`. Its NPC interaction
opens the same `ShopScreen` but with `faction = NEUTRAL` and the multiplier applied.

## Data model

### Migration `V3__economy_and_supply.sql`

```sql
-- Token ${AI} is replaced at runtime with AUTOINCREMENT (SQLite) or AUTO_INCREMENT (MySQL)

CREATE TABLE IF NOT EXISTS wallets (
    uuid               CHAR(36)    NOT NULL PRIMARY KEY,
    faction            VARCHAR(16),
    balance            BIGINT      NOT NULL DEFAULT 0,
    online_minutes     INTEGER     NOT NULL DEFAULT 0,
    created_at         BIGINT      NOT NULL,
    updated_at         BIGINT      NOT NULL,
    CONSTRAINT fk_wallets_player FOREIGN KEY (uuid) REFERENCES players(uuid)
);

CREATE TABLE IF NOT EXISTS wallet_tx (
    id                 INTEGER     NOT NULL PRIMARY KEY ${AI},
    wallet_uuid        CHAR(36)    NOT NULL,
    amount             BIGINT      NOT NULL,
    tx_type            VARCHAR(24) NOT NULL,
    counterparty_uuid  CHAR(36),
    reason_key         VARCHAR(64) NOT NULL,
    created_at         BIGINT      NOT NULL,
    extra_json         TEXT,
    CONSTRAINT fk_wt_wallet FOREIGN KEY (wallet_uuid) REFERENCES wallets(uuid)
);
CREATE INDEX IF NOT EXISTS idx_wt_wallet ON wallet_tx(wallet_uuid);
CREATE INDEX IF NOT EXISTS idx_wt_created ON wallet_tx(created_at);

CREATE TABLE IF NOT EXISTS treasury (
    faction            VARCHAR(16) NOT NULL PRIMARY KEY,
    balance            BIGINT      NOT NULL DEFAULT 0,
    updated_at         BIGINT      NOT NULL
);

CREATE TABLE IF NOT EXISTS treasury_tx (
    id                 INTEGER     NOT NULL PRIMARY KEY ${AI},
    faction            VARCHAR(16) NOT NULL,
    amount             BIGINT      NOT NULL,
    tx_type            VARCHAR(24) NOT NULL,
    counterparty_uuid  CHAR(36),
    reason_key         VARCHAR(64) NOT NULL,
    created_at         BIGINT      NOT NULL,
    extra_json         TEXT,
    CONSTRAINT fk_tt_treasury FOREIGN KEY (faction) REFERENCES treasury(faction)
);
CREATE INDEX IF NOT EXISTS idx_tt_faction ON treasury_tx(faction);
CREATE INDEX IF NOT EXISTS idx_tt_created ON treasury_tx(created_at);

CREATE TABLE IF NOT EXISTS shop_items (
    id                 INTEGER      NOT NULL PRIMARY KEY ${AI},
    faction            VARCHAR(16)  NOT NULL,
    item_id            VARCHAR(128) NOT NULL,
    count              INTEGER      NOT NULL DEFAULT 1,
    price              BIGINT       NOT NULL,
    min_role           VARCHAR(16)  NOT NULL DEFAULT 'SOLDIER',
    enabled            INTEGER      NOT NULL DEFAULT 1,
    category           VARCHAR(32)  NOT NULL DEFAULT 'general',
    CONSTRAINT uq_shop_faction_item UNIQUE (faction, item_id, category)
);
CREATE INDEX IF NOT EXISTS idx_shop_faction ON shop_items(faction);

CREATE TABLE IF NOT EXISTS shop_orders (
    id                 INTEGER     NOT NULL PRIMARY KEY ${AI},
    player_uuid        CHAR(36)    NOT NULL,
    shop_item_id       INTEGER     NOT NULL,
    count              INTEGER     NOT NULL,
    price_paid         BIGINT      NOT NULL,
    created_at         BIGINT      NOT NULL,
    CONSTRAINT fk_so_item FOREIGN KEY (shop_item_id) REFERENCES shop_items(id)
);
CREATE INDEX IF NOT EXISTS idx_so_player ON shop_orders(player_uuid);
CREATE INDEX IF NOT EXISTS idx_so_created ON shop_orders(created_at);

CREATE TABLE IF NOT EXISTS tax_policy (
    faction            VARCHAR(16) NOT NULL PRIMARY KEY,
    tax_percent        INTEGER     NOT NULL DEFAULT 10,
    updated_at         BIGINT      NOT NULL,
    updated_by_uuid    CHAR(36)
);
```

### Records

```java
public record WalletData(UUID uuid, FactionId faction, long balance, int onlineMinutes,
                         long createdAt, long updatedAt) {}

public record WalletTx(long id, UUID walletUuid, long amount, TxType type,
                       @Nullable UUID counterparty, String reasonKey, long createdAt,
                       @Nullable String extraJson) {}

public record TreasuryData(FactionId faction, long balance, long updatedAt) {}

public record TreasuryTx(long id, FactionId faction, long amount, TreasuryTxType type,
                         @Nullable UUID counterparty, String reasonKey, long createdAt,
                         @Nullable String extraJson) {}

public record ShopItem(int id, FactionId faction, String itemId, int count, long price,
                       Role minRole, boolean enabled, String category) {}

public record ShopOrder(long id, UUID playerUuid, int shopItemId, int count, long pricePaid,
                        long createdAt) {}
```

### Enums

```java
public enum TxType { INITIAL, SALARY, TRANSFER, PURCHASE, REFUND, FINE, REWARD, CONTRIBUTION, ADMIN }
public enum TreasuryTxType { INITIAL, SALARY_PAYMENT, TAX_RECEIPT, PURCHASE_REVENUE, DISBURSEMENT, FINE_RECEIPT, ADMIN }
```

## Network payloads

| Payload                | Direction | Fields                                              |
|------------------------|-----------|-----------------------------------------------------|
| `OpenShopPayload`      | S2C       | `int shopItemId, FactionId faction, ShopMode mode (FACTION/BLACK_MARKET)` |
| `ShopCatalogPayload`   | S2C       | `List<ShopItemView>` (id, itemId, count, displayPrice, minRole, category) |
| `ShopBuyPayload`       | C2S       | `int shopItemId`                                    |
| `WalletSnapshotPayload`| S2C       | `long balance, int taxPercent, FactionId faction`   |

`ShopMode.BLACK_MARKET` instructs the screen to display the multiplier visibly.

## Configuration (`WpConfig.economy`)

```java
ECONOMY_INITIAL_BALANCE              = builder.defineInRange("initialBalance", 100, 0, 1_000_000);
ECONOMY_TREASURY_INITIAL_BALANCE     = builder.defineInRange("treasuryInitialBalance", 5000, 0, 10_000_000);
ECONOMY_MAX_TX_AMOUNT                = builder.defineInRange("maxTxAmount", 1_000_000, 1, 1_000_000_000);
ECONOMY_SALARY_INTERVAL_MIN          = builder.defineInRange("salaryIntervalMin", 30, 1, 1440);
ECONOMY_SALARY_BY_ROLE               = builder.defineListAllowEmpty("salaryByRole", List.of(
    "CANDIDATE:0", "SOLDIER:50", "COMMANDER:100", "GENERAL:200"), WpConfig::validateString);
ECONOMY_DEFAULT_TAX_PERCENT          = builder.defineInRange("defaultTaxPercent", 10, 0, 25);
ECONOMY_BLACK_MARKET_MULTIPLIER      = builder.defineInRange("blackMarketMultiplier", 250, 100, 1000); // /100
ECONOMY_BLACK_MARKET_LOCATIONS       = builder.defineListAllowEmpty("blackMarketLocations", List.of(),
    WpConfig::validateString); // "dim;x,y,z"
ECONOMY_SUPPLY_DROP_INTERVAL_MIN     = builder.defineInRange("supplyDropIntervalMin", 120, 5, 1440);
ECONOMY_SUPPLY_DROP_DEFAULT_TIER     = builder.defineInRange("supplyDropDefaultTier", 2, 1, 5);
ECONOMY_SUPPLY_DROP_EXPIRY_MIN       = builder.defineInRange("supplyDropExpiryMin", 60, 1, 1440);
ECONOMY_DROP_TABLES                  = builder.defineListAllowEmpty("dropTables", defaults(),
    WpConfig::validateString); // "tier;item_id;count;weight"
```

## Correctness properties

| # | Property | Validates |
|---|----------|-----------|
| 1 | Wallet balance never goes negative across any sequence of credit/debit/transfer. | R1.2, R2.2 |
| 2 | Sum of all wallet_tx.amount equals (current balance − initial balance) for every wallet. | R2.4 |
| 3 | Treasury balance never goes negative across any sequence of credit/debit. | R3.5 |
| 4 | After `setTaxRate(p)` then salary payment, treasury increment equals `floor(salary * p / 100)`. | R5.3 |
| 5 | Successful transfer is atomic: either both wallets change or neither (no partial state). | R2.1 |
| 6 | A purchase produces exactly one wallet_tx and one treasury_tx and one shop_orders row. | R6.6 |
| 7 | A purchase with insufficient balance leaves wallet, treasury, orders unchanged. | R6.5 |
| 8 | Black-market purchase price equals `floor(base_price * multiplier / 100)`. | R7.2 |
| 9 | Salary payment is skipped when treasury < salary; treasury never goes below 0. | R4.2 |
| 10 | Online-minute counter persists across logout/login (sum equals real elapsed online). | R4.4, R4.5 |

## Error handling

| Failure | Strategy |
|---------|----------|
| Concurrent transfer (rare in single-server deployment) | Use `BEGIN IMMEDIATE` (already default in `Database.inTx`) which serializes writers in SQLite; for MySQL rely on `SELECT … FOR UPDATE` in DAO methods. |
| Item ID typo in shop_set | DAO validates resource location before insert; rejects with `wp.economy.shop.invalid_item`. |
| Wallet missing on credit (e.g. legacy player) | `ensureWallet(uuid, faction)` is called before any credit; if faction unknown, fail with `wp.economy.wallet.no_faction`. |
| Audit log write fails after main tx | Logged at WARN; main tx remains committed. Audit is best-effort. |

## Testing strategy

### Property tests (jqwik)

Tag format: `Feature: economy-and-supply, Property {n}`

| Property | Class                                | Generators |
|----------|--------------------------------------|------------|
| 1 — non-negative wallet | `WalletInvariantPropertyTest` | random sequences of credit/debit/transfer |
| 2 — wallet sum = balance| `WalletSumPropertyTest`        | same |
| 3 — non-negative treasury | `TreasuryInvariantPropertyTest` | random sequences of treasury credit/debit |
| 4 — tax math            | `TaxMathPropertyTest`          | random tax 0..25, salary 1..10000 |
| 5 — atomic transfer     | `TransferAtomicityPropertyTest`| H2 in-memory, fault injection between debit and credit |
| 6 — purchase row count  | `PurchaseRowCountPropertyTest` | random shop items + balances |
| 7 — purchase rollback   | `PurchaseRollbackPropertyTest` | balance < price |
| 8 — black-market price  | `BlackMarketPricePropertyTest` | random multipliers 100..1000 |
| 9 — salary skip on broke treasury | `SalarySkipPropertyTest` | random treasury balances |
| 10 — online minutes persistence | `OnlineMinutePersistencePropertyTest` | simulated login/logout cycles |

### Integration tests

- `EconomyMigrationIntegrationTest` — V3 migration applies cleanly on H2 and SQLite.
- `RoleCommandTableTest` — extends existing pattern to cover all `/wp money`,
  `/wp treasury`, `/wp shop` permission combinations.

### GameTests

- `SupplyDropGameTest` — supply drop spawns at faction spawn, contains items,
  expires correctly.
- `QuartermasterShopGameTest` — buying through GUI flows debit/credit and item delivery.
