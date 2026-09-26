-- V22: progress and leaderboard.
--
--  * point_transactions records the course an award belongs to, so a course can have its own leaderboard
--  * a learner can opt out of leaderboards (users.leaderboard_hidden)
--  * badge icons become icon names the app draws itself, instead of emoji characters

ALTER TABLE point_transactions ADD COLUMN course_id VARCHAR(36) NULL;

-- Best-effort backfill for awards that already exist.
UPDATE point_transactions SET course_id = source_id WHERE source_type = 'COURSE' AND source_id IS NOT NULL;
UPDATE point_transactions pt JOIN lessons l ON BIN_TO_UUID(l.id) = pt.source_id
SET pt.course_id = BIN_TO_UUID(l.course_id)
WHERE pt.source_type = 'LESSON' AND pt.course_id IS NULL;
UPDATE point_transactions pt JOIN assessments a ON BIN_TO_UUID(a.id) = pt.source_id
SET pt.course_id = BIN_TO_UUID(a.course_id)
WHERE pt.source_type = 'QUIZ' AND pt.course_id IS NULL;

CREATE INDEX idx_point_tx_course_time ON point_transactions (course_id, created_at);
CREATE INDEX idx_point_tx_time ON point_transactions (created_at);

ALTER TABLE users ADD COLUMN leaderboard_hidden BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE badges SET icon = CASE id
    WHEN 'FAST_LEARNER' THEN 'zap'
    WHEN 'QUIZ_ACE' THEN 'target'
    WHEN 'STREAK_MASTER' THEN 'flame'
    WHEN 'COURSE_GRADUATE' THEN 'graduation-cap'
    WHEN 'LEADERBOARD_TOP3' THEN 'trophy'
    WHEN 'WEB_DEV_PIONEER' THEN 'code'
    WHEN 'AI_EXPLORER' THEN 'cpu'
    WHEN 'SPEED_DEMON' THEN 'timer'
    WHEN 'COURSE_STARTER' THEN 'book-open'
    WHEN 'QUIZ_MASTER' THEN 'lightbulb'
    WHEN 'STREAK_7' THEN 'flame'
    WHEN 'STREAK_30' THEN 'award'
    WHEN 'XP_EXPLORER' THEN 'star'
    WHEN 'STREAM_SPECIALIST' THEN 'layers'
    ELSE 'award' END;
