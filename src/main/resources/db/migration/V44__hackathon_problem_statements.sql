-- Problem statements an organiser sets for a hosted hackathon, and the one each team chose.

CREATE TABLE hackathon_problem_statements (
    id                  BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    hackathon_id        BIGINT        NOT NULL,
    title               VARCHAR(200)  NOT NULL,
    description         TEXT          NOT NULL,
    track               VARCHAR(100)  NULL,
    requirements        TEXT          NULL,
    evaluation_criteria TEXT          NULL,
    resources_url       VARCHAR(1000) NULL,
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT hackathon_problem_event_fk FOREIGN KEY (hackathon_id) REFERENCES hackathons (id) ON DELETE CASCADE
);

CREATE INDEX idx_hackathon_problem_event ON hackathon_problem_statements (hackathon_id);

ALTER TABLE hackathon_teams
    ADD COLUMN problem_statement_id BIGINT NULL,
    ADD CONSTRAINT hackathon_team_problem_fk FOREIGN KEY (problem_statement_id) REFERENCES hackathon_problem_statements (id) ON DELETE SET NULL;
