-- V37: Add submission status column for strict submission lifecycle (DRAFT, SUBMITTED, LOCKED)
ALTER TABLE hackathon_submissions
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED';
