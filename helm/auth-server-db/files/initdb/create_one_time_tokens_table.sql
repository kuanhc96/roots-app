DROP TABLE IF EXISTS one_time_tokens;

-- Schema required by Spring Security's JdbcOneTimeTokenService.
-- Table and column names are hard-coded by that service and must not change.
-- Backs the magic-link token issued during the account-creation / email-verification flow.
-- `username` holds the user's email (the username in user_credential); widened to 255 to
-- match user_credential.email without truncation. No FK: rows are ephemeral (deleted on
-- consume, expired by TTL) and the only writer is the magic-link flow, which always inserts
-- an email that just came from user_credential.
CREATE TABLE one_time_tokens (
    token_value VARCHAR(36)  NOT NULL PRIMARY KEY,
    username    VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMP    NOT NULL
);
