-- A team can save a draft before turning its project in. Everything already stored was a finished submission.
-- A draft may be missing the repository link and description, so those two columns become optional.

ALTER TABLE hackathon_submissions
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    MODIFY COLUMN repo_url VARCHAR(500) NULL,
    MODIFY COLUMN description TEXT NULL,
    ADD CONSTRAINT hackathon_submission_status_check CHECK (status IN ('DRAFT', 'SUBMITTED'));
