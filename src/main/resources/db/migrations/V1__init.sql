-- WarProject schema V1: initial tables
-- Token ${AI} is replaced at runtime with AUTOINCREMENT (SQLite) or AUTO_INCREMENT (MySQL)

-- accounts: WGuard credentials
CREATE TABLE IF NOT EXISTS accounts (
    uuid              CHAR(36)    NOT NULL PRIMARY KEY,
    password_hash     VARCHAR(72) NOT NULL,
    registered_at     BIGINT      NOT NULL,
    last_login_at     BIGINT,
    failed_login_cnt  INTEGER     NOT NULL DEFAULT 0,
    cooldown_until    BIGINT      NOT NULL DEFAULT 0
);

-- players: game state
CREATE TABLE IF NOT EXISTS players (
    uuid              CHAR(36)    NOT NULL PRIMARY KEY,
    faction           VARCHAR(16),
    role              VARCHAR(16) NOT NULL DEFAULT 'CANDIDATE',
    rank              VARCHAR(32),
    status            VARCHAR(16) NOT NULL DEFAULT 'NEW',
    rp_name           VARCHAR(32),
    rp_surname        VARCHAR(32),
    collaborator      INTEGER     NOT NULL DEFAULT 0,
    collab_reason     TEXT,
    captured          INTEGER     NOT NULL DEFAULT 0,
    enemy_region_ticks INTEGER    NOT NULL DEFAULT 0,
    joined_at         BIGINT      NOT NULL,
    accepted_at       BIGINT,
    accepted_by_uuid  CHAR(36),
    accepted_by_name  VARCHAR(32),
    subdivision_id    INTEGER,
    CONSTRAINT fk_players_account FOREIGN KEY (uuid) REFERENCES accounts(uuid)
);

-- passports
CREATE TABLE IF NOT EXISTS passports (
    passport_id       VARCHAR(16) NOT NULL PRIMARY KEY,
    owner_uuid        CHAR(36)    NOT NULL,
    faction           VARCHAR(16) NOT NULL,
    rp_name           VARCHAR(32) NOT NULL,
    rp_surname        VARCHAR(32) NOT NULL,
    date_of_birth     CHAR(10)    NOT NULL,
    signature_seed    BIGINT      NOT NULL,
    status            VARCHAR(16) NOT NULL,
    accepted_at       BIGINT,
    accepted_by       VARCHAR(32),
    captured_by_uuid  CHAR(36),
    trophy            INTEGER     NOT NULL DEFAULT 0,
    created_at        BIGINT      NOT NULL,
    CONSTRAINT fk_passport_owner FOREIGN KEY (owner_uuid) REFERENCES players(uuid)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_passports_owner ON passports(owner_uuid);

-- subdivisions
CREATE TABLE IF NOT EXISTS subdivisions (
    id                INTEGER     NOT NULL PRIMARY KEY ${AI},
    faction           VARCHAR(16) NOT NULL,
    name              VARCHAR(32) NOT NULL,
    created_by        CHAR(36)    NOT NULL,
    created_at        BIGINT      NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_subdiv_faction_name ON subdivisions(faction, name);

CREATE TABLE IF NOT EXISTS subdivision_members (
    subdivision_id    INTEGER     NOT NULL,
    player_uuid       CHAR(36)    NOT NULL,
    joined_at         BIGINT      NOT NULL,
    PRIMARY KEY (subdivision_id, player_uuid),
    CONSTRAINT fk_sm_sub FOREIGN KEY (subdivision_id) REFERENCES subdivisions(id) ON DELETE CASCADE
);

-- audit_log
CREATE TABLE IF NOT EXISTS audit_log (
    id                INTEGER     NOT NULL PRIMARY KEY ${AI},
    ts_utc            BIGINT      NOT NULL,
    actor_uuid        CHAR(36),
    actor_name        VARCHAR(32),
    target_uuid       CHAR(36),
    target_name       VARCHAR(32),
    action            VARCHAR(32) NOT NULL,
    reason            TEXT,
    extra_json        TEXT
);
CREATE INDEX IF NOT EXISTS idx_audit_ts ON audit_log(ts_utc);
CREATE INDEX IF NOT EXISTS idx_audit_actor ON audit_log(actor_uuid);
CREATE INDEX IF NOT EXISTS idx_audit_target ON audit_log(target_uuid);

-- bans
CREATE TABLE IF NOT EXISTS bans (
    uuid              CHAR(36)    NOT NULL PRIMARY KEY,
    reason            TEXT        NOT NULL,
    banned_by         CHAR(36)    NOT NULL,
    banned_at         BIGINT      NOT NULL,
    expires_at        BIGINT      NOT NULL
);

-- mutes
CREATE TABLE IF NOT EXISTS mutes (
    uuid              CHAR(36)    NOT NULL PRIMARY KEY,
    reason            TEXT        NOT NULL,
    muted_by          CHAR(36)    NOT NULL,
    muted_at          BIGINT      NOT NULL,
    expires_at        BIGINT      NOT NULL
);

-- warns
CREATE TABLE IF NOT EXISTS warns (
    id                INTEGER     NOT NULL PRIMARY KEY ${AI},
    uuid              CHAR(36)    NOT NULL,
    reason            TEXT        NOT NULL,
    issued_by         CHAR(36)    NOT NULL,
    issued_at         BIGINT      NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_warns_uuid ON warns(uuid);

-- cooldowns
CREATE TABLE IF NOT EXISTS cooldowns (
    uuid              CHAR(36)    NOT NULL,
    cd_type           VARCHAR(32) NOT NULL,
    expires_at        BIGINT      NOT NULL,
    PRIMARY KEY (uuid, cd_type)
);

-- ranks reference table
CREATE TABLE IF NOT EXISTS ranks (
    faction           VARCHAR(16) NOT NULL,
    role              VARCHAR(16) NOT NULL,
    rank              VARCHAR(32) NOT NULL,
    ord               INTEGER     NOT NULL,
    PRIMARY KEY (faction, rank)
);

-- passport_sequence: for generating unique passport IDs
CREATE TABLE IF NOT EXISTS passport_sequence (
    prefix            VARCHAR(4)  NOT NULL PRIMARY KEY,
    last_n            INTEGER     NOT NULL DEFAULT 0
);
