-- V26: how many voice answers a learner has transcribed each day (audio itself is never stored).
CREATE TABLE interview_voice_usage (
    user_id BIGINT NOT NULL,
    day     DATE   NOT NULL,
    clips   INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, day),
    CONSTRAINT interview_voice_usage_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
