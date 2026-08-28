package com.roots.account_management.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.roots.account_management.model.UserCredential;
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

    public Optional<UserCredential> findByUserGUID(String userGUID) {
        var results = jdbcTemplate.query(
                "SELECT id, user_guid, email, name, password, is_mfa_enabled, is_email_verified, is_password_change_required FROM user_credential WHERE user_guid = ?",
                ROW_MAPPER,
                userGUID
        );
        return results.stream().findFirst();
    }

    public List<UserCredential> findAllPaged(int size, int offset) {
        return jdbcTemplate.query(
                "SELECT id, user_guid, email, name, password, is_mfa_enabled, is_email_verified, is_password_change_required "
                        + "FROM user_credential ORDER BY id ASC LIMIT ? OFFSET ?",
                ROW_MAPPER,
                size,
                offset
        );
    }

    public long countAll() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_credential", Long.class);
        return count == null ? 0L : count;
    }

    public void deleteById(long id) {
        jdbcTemplate.update("DELETE FROM user_credential WHERE id = ?", id);
    }

    public long insert(UserCredential userCredential) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO user_credential (user_guid, email, name, password, is_mfa_enabled, is_email_verified, is_password_change_required) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, userCredential.userGUID());
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

    public int setMfaEnabledByUserGUID(Long userId, boolean mfaEnabled) {
        return jdbcTemplate.update(
                "UPDATE user_credential SET is_mfa_enabled = ? WHERE id = ?",
                mfaEnabled,
                userId
        );
    }
}
