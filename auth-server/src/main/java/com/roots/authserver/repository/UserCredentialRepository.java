package com.roots.authserver.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
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
            rs.getString("name"),
            rs.getString("password"),
            rs.getBoolean("is_mfa_enabled"),
            rs.getBoolean("is_email_verified"),
            rs.getBoolean("is_password_change_required")
    );

    public Optional<UserCredential> findByEmail(String email) {
        var results = jdbcTemplate.query(
                "SELECT id, user_guid, email, name, password, is_mfa_enabled, is_email_verified, is_password_change_required FROM user_credential WHERE email = ?",
                ROW_MAPPER,
                email
        );
        return results.stream().findFirst();
    }

    public Optional<UserCredential> findById(long id) {
        var results = jdbcTemplate.query(
                "SELECT id, user_guid, email, name, password, is_mfa_enabled, is_email_verified, is_password_change_required FROM user_credential WHERE id = ?",
                ROW_MAPPER,
                id
        );
        return results.stream().findFirst();
    }

    public long insert(UserCredential userCredential) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO user_credential (user_guid, email, name, password, is_mfa_enabled, is_email_verified, is_password_change_required) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, userCredential.userGuid());
            ps.setString(2, userCredential.email());
            ps.setString(3, userCredential.name());
            ps.setString(4, userCredential.password());
            ps.setBoolean(5, userCredential.mfaEnabled());
            ps.setBoolean(6, userCredential.emailVerified());
            ps.setBoolean(7, userCredential.passwordChangeRequired());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void setMfaEnabled(String email, boolean enabled) {
        jdbcTemplate.update(
                "UPDATE user_credential SET is_mfa_enabled = ? WHERE email = ?",
                enabled, email
        );
    }

    public void verifyEmail(String email) {
        jdbcTemplate.update(
                "UPDATE user_credential SET is_email_verified = ? WHERE email = ?",
                true, email
        );
    }

    public void setPasswordChangeRequired(String email, boolean required) {
        jdbcTemplate.update(
                "UPDATE user_credential SET is_password_change_required = ? WHERE email = ?",
                required, email
        );
    }

    public void updatePassword(String email, String encodedPassword) {
        jdbcTemplate.update(
                "UPDATE user_credential SET password = ? WHERE email = ?",
                encodedPassword, email
        );
    }
}
