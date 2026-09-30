-- V36: how closely an AI-generated question ties to its source transcript (null for manual/imported ones)

ALTER TABLE questions ADD COLUMN grounding VARCHAR(8) NULL;
ALTER TABLE questions ADD CONSTRAINT questions_grounding_check CHECK (grounding IS NULL OR grounding IN ('DIRECT', 'RELATED'));
