-- V28: hackathon banners can be uploaded (kept in private storage and served by the API) instead of only linked.
ALTER TABLE hackathons
    ADD COLUMN banner_key VARCHAR(300) NULL,
    ADD COLUMN banner_mime VARCHAR(60) NULL;
