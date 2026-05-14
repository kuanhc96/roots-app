package com.roots.authserver.repository;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.roots.authserver.model.UserCredential;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class UserCredentialRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<UserCredential> ROW_MAPPER = (rs, rowNum) -> new UserCredential(
            rs.getLong("id"),
            rs.getString("user_guid"),
            rs.getString("email"),
            rs.getString("password"),
            rs.getBoolean("is_mfa_enabled"),
            rs.getBoolean("is_email_verified")
    );

    public Optional<UserCredential> findByEmail(String email) {
        var results = jdbcTemplate.query(
                "SELECT id, user_guid, email, password, is_mfa_enabled, is_email_verified FROM user_credential WHERE email = ?",
                ROW_MAPPER,
                email
        );
        return results.stream().findFirst();
    }

    public void setMfaEnabled(String email, boolean enabled) {
        jdbcTemplate.update(
                "UPDATE user_credential SET is_mfa_enabled = ? WHERE email = ?",
                enabled, email
        );
    }
}
