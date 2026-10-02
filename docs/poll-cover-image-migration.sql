-- For deployments with Hibernate ddl-auto=update disabled.
-- Apply once before deploying poll cover image support.
ALTER TABLE festival_polls
    ADD COLUMN image_url VARCHAR(2048) NULL,
    ADD COLUMN image_storage_key VARCHAR(1024) NULL,
    ADD COLUMN image_original_filename VARCHAR(255) NULL;
