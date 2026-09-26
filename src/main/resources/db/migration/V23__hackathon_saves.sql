-- V23: hackathons are external listings. Learners save them and register on the organiser's own site, so there is no
-- registration to pay points for: registrations become saves, and the XP reward goes away.
RENAME TABLE hackathon_registrations TO hackathon_saves;
ALTER TABLE hackathon_saves
    DROP COLUMN points_claimed,
    RENAME COLUMN registered_at TO saved_at;
ALTER TABLE hackathons DROP COLUMN points_reward;
