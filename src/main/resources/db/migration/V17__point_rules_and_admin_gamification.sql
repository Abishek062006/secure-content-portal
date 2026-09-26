-- V17: Point Rules for central XP management

CREATE TABLE point_rules (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    action_type VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    points INT NOT NULL DEFAULT 0,
    description VARCHAR(255),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE = InnoDB;

INSERT INTO point_rules (id, action_type, display_name, points, description) VALUES
('LESSON_COMPLETED', 'LESSON_COMPLETED', 'Lesson Completion', 10, 'Awarded when a learner completes a video lesson'),
('QUIZ_PASSED', 'QUIZ_PASSED', 'Quiz Passed', 20, 'Awarded when a learner passes an assessment quiz'),
('COURSE_COMPLETED', 'COURSE_COMPLETED', 'Course Completion', 100, 'Awarded when a learner completes all lessons in a course'),
('DAILY_CHECKIN', 'DAILY_CHECKIN', 'Daily Check-in', 5, 'Base reward for daily login check-in');

ALTER TABLE point_transactions ADD COLUMN source_type VARCHAR(50);
ALTER TABLE point_transactions ADD COLUMN source_id VARCHAR(100);

