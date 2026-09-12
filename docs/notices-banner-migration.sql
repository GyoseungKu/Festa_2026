-- Apply once to an existing notices table before deploying without Hibernate schema updates.
ALTER TABLE notices ADD COLUMN banner BOOLEAN NOT NULL DEFAULT FALSE;
