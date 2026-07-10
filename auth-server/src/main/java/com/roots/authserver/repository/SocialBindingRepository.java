package com.roots.authserver.repository;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.roots.authserver.enums.SocialProvider;
import com.roots.authserver.model.SocialBinding;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class SocialBindingRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<SocialBinding> ROW_MAPPER = (rs, rowNum) -> new SocialBinding(
            rs.getLong("id"),
            rs.getLong("user_id"),
            SocialProvider.fromValue(rs.getString("social_provider")),
            rs.getString("social_user_id")
    );

    public Optional<SocialBinding> findByProviderAndSocialUserId(SocialProvider socialProvider, String socialUserId) {
        var results = jdbcTemplate.query(
                "SELECT id, user_id, social_provider, social_user_id FROM social_binding WHERE social_provider = ? AND social_user_id = ?",
                ROW_MAPPER,
                socialProvider.getValue(), socialUserId
        );
        return results.stream().findFirst();
    }

    public void insert(long userId, SocialProvider socialProvider, String socialUserId) {
        jdbcTemplate.update(
                "INSERT INTO social_binding (user_id, social_provider, social_user_id) VALUES (?, ?, ?)",
                userId, socialProvider.getValue(), socialUserId
        );
    }
}
