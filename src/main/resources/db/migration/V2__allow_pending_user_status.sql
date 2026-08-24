ALTER TABLE users
    DROP CONSTRAINT ck_users_status;

ALTER TABLE users
    ADD CONSTRAINT ck_users_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'DISABLED'));
