-- V29: an admin can share a hackathon to the feed. Deleting the hackathon keeps the post as plain text, as with course posts.
ALTER TABLE posts
    ADD COLUMN hackathon_id BIGINT NULL,
    ADD CONSTRAINT posts_hackathon_fk FOREIGN KEY (hackathon_id) REFERENCES hackathons (id) ON DELETE SET NULL;
