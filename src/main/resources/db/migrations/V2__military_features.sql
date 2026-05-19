-- WarProject schema V2: military features expansion
-- Token ${AI} is replaced at runtime with AUTOINCREMENT (SQLite) or AUTO_INCREMENT (MySQL)

-- events
CREATE TABLE IF NOT EXISTS events (
    id              INTEGER     NOT NULL PRIMARY KEY ${AI},
    name            VARCHAR(64) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    creator_uuid    CHAR(36)    NOT NULL,
    spawn_x         INTEGER,
    spawn_y         INTEGER,
    spawn_z         INTEGER,
    spawn_dimension VARCHAR(128),
    created_at      BIGINT      NOT NULL,
    completed_at    BIGINT,
    participant_count INTEGER   NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_events_status ON events(status);
CREATE INDEX IF NOT EXISTS idx_events_creator ON events(creator_uuid);

-- event_participants
CREATE TABLE IF NOT EXISTS event_participants (
    event_id        INTEGER     NOT NULL,
    player_uuid     CHAR(36)    NOT NULL,
    joined_at       BIGINT      NOT NULL,
    PRIMARY KEY (event_id, player_uuid),
    CONSTRAINT fk_ep_event FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE
);

-- truces
CREATE TABLE IF NOT EXISTS truces (
    id                  INTEGER     NOT NULL PRIMARY KEY ${AI},
    proposing_faction   VARCHAR(16) NOT NULL,
    target_faction      VARCHAR(16) NOT NULL,
    duration_minutes    INTEGER     NOT NULL,
    started_at          BIGINT      NOT NULL,
    ended_at            BIGINT,
    status              VARCHAR(16) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_truces_status ON truces(status);

-- award_definitions
CREATE TABLE IF NOT EXISTS award_definitions (
    id              INTEGER      NOT NULL PRIMARY KEY ${AI},
    name            VARCHAR(64)  NOT NULL UNIQUE,
    description     VARCHAR(256) NOT NULL,
    icon_id         VARCHAR(64)  NOT NULL DEFAULT 'default_medal',
    created_at      BIGINT       NOT NULL
);

-- player_awards
CREATE TABLE IF NOT EXISTS player_awards (
    id              INTEGER     NOT NULL PRIMARY KEY ${AI},
    player_uuid     CHAR(36)    NOT NULL,
    award_id        INTEGER     NOT NULL,
    granted_by_uuid CHAR(36)    NOT NULL,
    granted_at      BIGINT      NOT NULL,
    CONSTRAINT fk_pa_award FOREIGN KEY (award_id) REFERENCES award_definitions(id),
    CONSTRAINT uq_player_award UNIQUE (player_uuid, award_id)
);
CREATE INDEX IF NOT EXISTS idx_pa_player ON player_awards(player_uuid);
CREATE INDEX IF NOT EXISTS idx_pa_award ON player_awards(award_id);
