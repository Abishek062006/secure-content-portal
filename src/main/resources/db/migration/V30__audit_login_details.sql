-- Every login (and failed attempt) is now an audit entry too, with where it came from, so an admin can filter
-- the log by action and see who signed in, when, and from what IP/device.
ALTER TABLE audit_logs
    ADD COLUMN ip_address VARCHAR(64) NULL,
    ADD COLUMN user_agent VARCHAR(300) NULL;

CREATE INDEX idx_audit_logs_action ON audit_logs (action);
CREATE INDEX idx_audit_logs_actor_email ON audit_logs (actor_email);
