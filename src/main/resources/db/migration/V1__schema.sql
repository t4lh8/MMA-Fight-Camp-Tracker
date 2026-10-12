-- Users. Passwords are stored as BCrypt hashes only.
CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(32)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(16)  NOT NULL CHECK (role IN ('ATHLETE', 'COACH')),
    weight_class  VARCHAR(24),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- One row per training session. Load = duration x RPE (session RPE method).
CREATE TABLE training_session (
    id           BIGSERIAL PRIMARY KEY,
    owner_id     BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    session_date DATE        NOT NULL,
    discipline   VARCHAR(16) NOT NULL CHECK (discipline IN
                 ('STRIKING', 'WRESTLING', 'BJJ', 'SPARRING', 'STRENGTH', 'CONDITIONING')),
    duration_min INT         NOT NULL CHECK (duration_min BETWEEN 1 AND 480),
    rpe          INT         NOT NULL CHECK (rpe BETWEEN 1 AND 10),
    rounds       INT         CHECK (rounds BETWEEN 0 AND 50),
    notes        VARCHAR(500)
);
CREATE INDEX training_session_owner_date ON training_session (owner_id, session_date);

CREATE TABLE fight (
    id           BIGSERIAL PRIMARY KEY,
    owner_id     BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    fight_date   DATE        NOT NULL,
    opponent     VARCHAR(80) NOT NULL,
    event_name   VARCHAR(120),
    result       VARCHAR(8)  NOT NULL CHECK (result IN ('WIN', 'LOSS', 'DRAW', 'NC')),
    method       VARCHAR(12) NOT NULL CHECK (method IN ('KO_TKO', 'SUBMISSION', 'DECISION', 'OTHER')),
    end_round    INT         NOT NULL CHECK (end_round BETWEEN 1 AND 5),
    -- a draw or no contest cannot end by knockout or submission
    CHECK (result IN ('WIN', 'LOSS') OR method IN ('DECISION', 'OTHER'))
);
CREATE INDEX fight_owner_date ON fight (owner_id, fight_date);

CREATE TABLE weigh_in (
    id        BIGSERIAL PRIMARY KEY,
    owner_id  BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    weigh_date DATE        NOT NULL,
    kg        NUMERIC(5,2) NOT NULL CHECK (kg BETWEEN 40 AND 200),
    UNIQUE (owner_id, weigh_date)
);

-- An athlete can give a coach read-only access to their dashboard.
CREATE TABLE coach_access (
    athlete_id BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    coach_id   BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (athlete_id, coach_id),
    CHECK (athlete_id <> coach_id)
);

-- Security-relevant events: logins, lockouts, sharing, denied access.
CREATE TABLE audit_event (
    id          BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    username    VARCHAR(32) NOT NULL,
    action      VARCHAR(32) NOT NULL,
    detail      VARCHAR(200),
    client_ip   VARCHAR(45)
);
CREATE INDEX audit_event_user_time ON audit_event (username, occurred_at DESC);

-- Weekly training load per athlete, used by the dashboard chart.
CREATE VIEW weekly_load AS
SELECT owner_id,
       date_trunc('week', session_date)::date AS week_start,
       COUNT(*)                               AS sessions,
       SUM(duration_min)                      AS minutes,
       SUM(duration_min * rpe)                AS load
FROM training_session
GROUP BY owner_id, date_trunc('week', session_date);
