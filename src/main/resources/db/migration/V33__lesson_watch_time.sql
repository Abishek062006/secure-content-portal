-- A running total of actual watch time per lesson, separate from position_seconds (where playback currently
-- sits). Only forward progress within a sane per-save window counts — see LessonProgress.record — so a seek
-- doesn't inflate it; this is an honest approximation of time spent, not a frame-accurate video analytics log.
ALTER TABLE lesson_progress ADD COLUMN watched_seconds INT NOT NULL DEFAULT 0;
