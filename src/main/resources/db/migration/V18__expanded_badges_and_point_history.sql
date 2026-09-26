-- V18: Expanded Badge Set for Gamification System

INSERT INTO badges (id, title, description, category, icon, points_reward, rarity) VALUES
('COURSE_STARTER', 'Course Starter', 'Completed your first full course on GradientNovaAI', 'MILESTONE', '🎓', 100, 'COMMON'),
('QUIZ_MASTER', 'Quiz Master', 'Passed 5 quiz assessments', 'MASTERY', '🧠', 150, 'RARE'),
('STREAK_7', 'Learning Streak', 'Maintained a 7-day consecutive learning streak', 'STREAK', '🔥', 200, 'RARE'),
('STREAK_30', 'Dedicated Learner', 'Maintained a 30-day consecutive learning streak', 'STREAK', '⚡', 500, 'LEGENDARY'),
('XP_EXPLORER', 'XP Explorer', 'Accumulated 500 Total XP across learning activities', 'MILESTONE', '⭐', 150, 'RARE'),
('STREAM_SPECIALIST', 'Stream Specialist', 'Completed multiple courses within a single learning stream', 'STREAM', '🏆', 300, 'EPIC')
ON DUPLICATE KEY UPDATE title=VALUES(title), description=VALUES(description), category=VALUES(category), icon=VALUES(icon), points_reward=VALUES(points_reward), rarity=VALUES(rarity);
