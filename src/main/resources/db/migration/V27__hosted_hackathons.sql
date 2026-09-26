-- V27: hackathons hosted on the platform: teams, submissions, judges and scores, alongside the external listings.
--
-- A hosted event has one timeline: registration (until registration_deadline), building (event_start_date to event_end_date, the
-- submission deadline), judging (from event_end_date), and results once an admin publishes them.

ALTER TABLE hackathons
    ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'EXTERNAL',
    ADD COLUMN rules TEXT NULL,
    ADD COLUMN tracks VARCHAR(500) NULL,
    ADD COLUMN prizes TEXT NULL,
    ADD COLUMN min_team_size INT NOT NULL DEFAULT 1,
    ADD COLUMN max_team_size INT NOT NULL DEFAULT 4,
    ADD COLUMN results_published_at DATETIME(6) NULL,
    MODIFY registration_url VARCHAR(1000) NULL;

CREATE TABLE hackathon_teams (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    hackathon_id BIGINT       NOT NULL,
    name         VARCHAR(80)  NOT NULL,
    track        VARCHAR(100) NULL,
    invite_code  VARCHAR(16)  NOT NULL,
    leader_id    BIGINT       NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    CONSTRAINT uk_hackathon_team_invite UNIQUE (invite_code),
    CONSTRAINT uk_hackathon_team_name UNIQUE (hackathon_id, name),
    CONSTRAINT hackathon_team_event_fk FOREIGN KEY (hackathon_id) REFERENCES hackathons (id) ON DELETE CASCADE,
    CONSTRAINT hackathon_team_leader_fk FOREIGN KEY (leader_id) REFERENCES users (id) ON DELETE CASCADE
);

-- One team per learner per event: hackathon_id is repeated here so the database enforces it.
CREATE TABLE hackathon_team_members (
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    team_id      BIGINT      NOT NULL,
    hackathon_id BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    joined_at    DATETIME(6) NOT NULL,
    CONSTRAINT uk_hackathon_member_team UNIQUE (team_id, user_id),
    CONSTRAINT uk_hackathon_member_event UNIQUE (hackathon_id, user_id),
    CONSTRAINT hackathon_member_team_fk FOREIGN KEY (team_id) REFERENCES hackathon_teams (id) ON DELETE CASCADE,
    CONSTRAINT hackathon_member_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE hackathon_submissions (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    team_id      BIGINT       NOT NULL,
    hackathon_id BIGINT       NOT NULL,
    title        VARCHAR(150) NOT NULL,
    repo_url     VARCHAR(500) NOT NULL,
    demo_url     VARCHAR(500) NULL,
    description  TEXT         NOT NULL,
    submitted_at DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    CONSTRAINT uk_hackathon_submission_team UNIQUE (team_id),
    CONSTRAINT hackathon_submission_team_fk FOREIGN KEY (team_id) REFERENCES hackathon_teams (id) ON DELETE CASCADE,
    CONSTRAINT hackathon_submission_event_fk FOREIGN KEY (hackathon_id) REFERENCES hackathons (id) ON DELETE CASCADE
);

CREATE TABLE hackathon_judges (
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    hackathon_id BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    assigned_at  DATETIME(6) NOT NULL,
    CONSTRAINT uk_hackathon_judge UNIQUE (hackathon_id, user_id),
    CONSTRAINT hackathon_judge_event_fk FOREIGN KEY (hackathon_id) REFERENCES hackathons (id) ON DELETE CASCADE,
    CONSTRAINT hackathon_judge_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE hackathon_scores (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT       NOT NULL,
    judge_id      BIGINT       NOT NULL,
    innovation    INT          NOT NULL,
    execution     INT          NOT NULL,
    impact        INT          NOT NULL,
    presentation  INT          NOT NULL,
    comment       VARCHAR(1000) NULL,
    scored_at     DATETIME(6)  NOT NULL,
    CONSTRAINT uk_hackathon_score UNIQUE (submission_id, judge_id),
    CONSTRAINT hackathon_score_submission_fk FOREIGN KEY (submission_id) REFERENCES hackathon_submissions (id) ON DELETE CASCADE,
    CONSTRAINT hackathon_score_judge_fk FOREIGN KEY (judge_id) REFERENCES users (id) ON DELETE CASCADE
);

INSERT INTO badges (id, title, description, category, icon, points_reward, rarity) VALUES
('HACKATHON_BUILDER', 'Hackathon Builder', 'Submitted a project to a GradientNova hackathon', 'MILESTONE', 'code', 100, 'RARE'),
('HACKATHON_WINNER', 'Hackathon Winner', 'Finished in the top three of a GradientNova hackathon', 'MASTERY', 'trophy', 300, 'LEGENDARY');
