-- Certificates for hosted hackathons (one per member, with a code anyone can verify), and flags so each deadline reminder goes out once.

CREATE TABLE hackathon_certificates (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code             VARCHAR(50)  NOT NULL,
    hackathon_id     BIGINT       NOT NULL,
    user_id          BIGINT       NOT NULL,
    recipient_name   VARCHAR(200) NOT NULL,
    hackathon_title  VARCHAR(200) NOT NULL,
    team_name        VARCHAR(150) NULL,
    certificate_type VARCHAR(30)  NOT NULL,
    ranking          INT          NULL,
    issued_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_hackathon_cert_code UNIQUE (code),
    CONSTRAINT uk_hackathon_cert_user UNIQUE (hackathon_id, user_id),
    CONSTRAINT hackathon_cert_type_check CHECK (certificate_type IN ('WINNER', 'RUNNER_UP', 'PARTICIPATION')),
    CONSTRAINT hackathon_cert_event_fk FOREIGN KEY (hackathon_id) REFERENCES hackathons (id) ON DELETE CASCADE,
    CONSTRAINT hackathon_cert_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_hackathon_cert_user ON hackathon_certificates (user_id);

ALTER TABLE hackathons
    ADD COLUMN reminded_24h BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN reminded_1h  BOOLEAN NOT NULL DEFAULT FALSE;
