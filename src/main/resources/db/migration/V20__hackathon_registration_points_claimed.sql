-- The HackathonRegistration entity has a points_claimed column that V19 never created; it only existed on databases where
-- Hibernate had auto-updated the schema. Add it for everyone else, and do nothing where it is already there.
SET @has_column := (SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'hackathon_registrations' AND column_name = 'points_claimed');
SET @ddl := IF(@has_column = 0,
               'ALTER TABLE hackathon_registrations ADD COLUMN points_claimed INT NOT NULL DEFAULT 0',
               'SELECT 1');
PREPARE add_points_claimed FROM @ddl;
EXECUTE add_points_claimed;
DEALLOCATE PREPARE add_points_claimed;
