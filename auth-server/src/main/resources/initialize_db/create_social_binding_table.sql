DROP TABLE IF EXISTS social_binding;

-- Links a local account to an external identity provider's stable user id.
-- `social_user_id` holds the provider's subject (Google id_token `sub`) — the
-- provider-side primary key; the provider-side email can change, the sub cannot,
-- so login resolution is sub-first. It is UNIQUE on its own (provider id schemes
-- are effectively globally unique — Google's sub is a 21-digit numeric string),
-- so lookups key on social_user_id alone; `social_provider` is informational.
-- ON DELETE CASCADE so deleting a credential (e.g. account-management's
-- DELETE /api/account/test, which knows nothing about this table) takes its
-- bindings with it.
-- Note: on an existing database this script must be applied manually — the
-- /docker-entrypoint-initdb.d scripts only run on a fresh MySQL volume.
CREATE TABLE social_binding (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    social_provider VARCHAR(50)  NOT NULL,
    social_user_id  VARCHAR(255) NOT NULL,
    created_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (social_user_id),
    FOREIGN KEY (user_id) REFERENCES user_credential (id) ON DELETE CASCADE
);
