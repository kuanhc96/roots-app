CREATE TABLE user_credential (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_guid     VARCHAR(36)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password      VARCHAR(255) NOT NULL,
    creation_date TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_date   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (user_guid),
    UNIQUE (email)
);

CREATE TABLE role (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    role_guid     VARCHAR(36)  NOT NULL,
    credential_id BIGINT       NOT NULL,
    role_name     VARCHAR(100) NOT NULL,
    creation_date TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_date   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (credential_id) REFERENCES user_credential (id)
);
