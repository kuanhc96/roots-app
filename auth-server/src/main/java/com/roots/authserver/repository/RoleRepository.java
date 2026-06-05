package com.roots.authserver.repository;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class RoleRepository {

    private final JdbcTemplate jdbcTemplate;

    public void insert(long credentialId, String roleName) {
        jdbcTemplate.update(
                "INSERT INTO role (role_guid, credential_id, role_name) VALUES (?, ?, ?)",
                UUID.randomUUID().toString(), credentialId, roleName
        );
    }
}
