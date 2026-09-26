-- V16: Gamification system (Points, Badges, Leaderboard, Login Streaks)

CREATE TABLE user_gamification (
    user_id BIGINT NOT NULL PRIMARY KEY,
    total_points INT NOT NULL DEFAULT 0,
    current_streak INT NOT NULL DEFAULT 0,
    max_streak INT NOT NULL DEFAULT 0,
    last_checkin_date DATE,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT user_gamification_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE badges (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    icon VARCHAR(50) NOT NULL,
    points_reward INT NOT NULL DEFAULT 100,
    rarity VARCHAR(20) NOT NULL DEFAULT 'COMMON'
) ENGINE = InnoDB;

CREATE TABLE user_badges (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    badge_id VARCHAR(64) NOT NULL,
    unlocked_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_user_badge UNIQUE (user_id, badge_id),
    CONSTRAINT user_badges_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT user_badges_badge_fk FOREIGN KEY (badge_id) REFERENCES badges (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE point_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount INT NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    stream VARCHAR(100),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT point_transactions_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX idx_point_tx_user_date ON point_transactions(user_id, created_at);
CREATE INDEX idx_point_tx_date_stream ON point_transactions(created_at, stream);
CREATE INDEX idx_user_gamification_points ON user_gamification(total_points DESC);

-- Seed initial Badges
INSERT INTO badges (id, title, description, category, icon, points_reward, rarity) VALUES
('FAST_LEARNER', 'Fast Learner', 'Completed your first video lesson', 'MILESTONE', '⚡', 50, 'COMMON'),
('QUIZ_ACE', 'Quiz Ace', 'Scored 100% on any quiz assessment', 'MASTERY', '🎯', 100, 'RARE'),
('STREAK_MASTER', 'Streak Master', 'Maintained a 3-day active login streak', 'STREAK', '🔥', 150, 'RARE'),
('COURSE_GRADUATE', 'Course Graduate', 'Completed all lessons in a course', 'MILESTONE', '🎓', 300, 'EPIC'),
('LEADERBOARD_TOP3', 'Podium Scholar', 'Reached the top 3 on the global leaderboard', 'MASTERY', '🏆', 500, 'LEGENDARY'),
('WEB_DEV_PIONEER', 'Web Dev Pioneer', 'Completed a course in Engineering / Web Development', 'STREAM', '💻', 200, 'RARE'),
('AI_EXPLORER', 'AI Explorer', 'Completed a course in AI & Machine Learning', 'STREAM', '🤖', 200, 'RARE'),
('SPEED_DEMON', 'Quiz Sprint', 'Passed 3 quizzes in a single day', 'MASTERY', '🚀', 250, 'EPIC');
