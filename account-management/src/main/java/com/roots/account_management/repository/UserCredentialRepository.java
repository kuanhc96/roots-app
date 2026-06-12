package com.roots.account_management.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
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
            rs.getBoolean("is_email_verified")
    );

    public Optional<UserCredential> findByEmail(String email) {
        var results = jdbcTemplate.query(
                "SELECT id, user_guid, email, name, password, is_mfa_enabled, is_email_verified FROM user_credential WHERE email = ?",
                ROW_MAPPER,
                email
        );
        return results.stream().findFirst();
    }

    public Optional<UserCredential> findByUserGuid(String userGuid) {
        var results = jdbcTemplate.query(
                "SELECT id, user_guid, email, name, password, is_mfa_enabled, is_email_verified FROM user_credential WHERE user_guid = ?",
                ROW_MAPPER,
                userGuid
        );
        return results.stream().findFirst();
    }

    public void deleteById(long id) {
        jdbcTemplate.update("DELETE FROM user_credential WHERE id = ?", id);
    }

    public long insert(UserCredential userCredential) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO user_credential (user_guid, email, name, password, is_mfa_enabled, is_email_verified) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, userCredential.userGuid());
            ps.setString(2, userCredential.email());
            ps.setString(3, userCredential.name());
            ps.setString(4, userCredential.password());
            ps.setBoolean(5, userCredential.mfaEnabled());
            ps.setBoolean(6, userCredential.emailVerified());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}
