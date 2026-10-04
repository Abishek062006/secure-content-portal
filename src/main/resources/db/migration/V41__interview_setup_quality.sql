-- V41: how well the camera setup worked during an interview, measured by the learner's browser: the share of the time their face
-- was in frame and the share of the time the lighting was good. Feedback only, and optional (null when the camera wasn't used).

ALTER TABLE mock_interview_sessions ADD COLUMN face_visible_percent INT NULL;
ALTER TABLE mock_interview_sessions ADD COLUMN lighting_good_percent INT NULL;
ALTER TABLE mock_interview_sessions ADD CONSTRAINT mock_interview_sessions_face_check
    CHECK (face_visible_percent IS NULL OR face_visible_percent BETWEEN 0 AND 100);
ALTER TABLE mock_interview_sessions ADD CONSTRAINT mock_interview_sessions_lighting_check
    CHECK (lighting_good_percent IS NULL OR lighting_good_percent BETWEEN 0 AND 100);
