-- Per-item view tracking ended up as counters on content_items (view_count,
-- last_viewed_at); this per-view log table was never read or written.
DROP TABLE content_views;
