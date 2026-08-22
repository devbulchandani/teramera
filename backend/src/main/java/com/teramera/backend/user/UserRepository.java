package com.teramera.backend.user;

import com.teramera.backend.db.SqlExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {

    private final SqlExecutor db;

    public UserRepository(SqlExecutor db) {
        this.db = db;
    }

    public record User(String id, String phone, String email, String name, String avatarUrl) {}

    public Optional<User> byId(String id) {
        var rows = db.query("SELECT * FROM users WHERE id = ?", List.of(id));
        return rows.isEmpty() ? Optional.empty() : Optional.of(map(rows.getFirst()));
    }

    public Optional<User> byPhone(String phone) {
        var rows = db.query("SELECT * FROM users WHERE phone = ?", List.of(phone));
        return rows.isEmpty() ? Optional.empty() : Optional.of(map(rows.getFirst()));
    }

    public Optional<User> byEmail(String email) {
        var rows = db.query("SELECT * FROM users WHERE email = ?", List.of(email));
        return rows.isEmpty() ? Optional.empty() : Optional.of(map(rows.getFirst()));
    }

    public User createPhoneUser(String phone) {
        return insert(new User(UUID.randomUUID().toString(), phone, null, null, null));
    }

    public User createGoogleUser(String email, String name, String avatarUrl) {
        return insert(new User(UUID.randomUUID().toString(), null, email, name, avatarUrl));
    }

    public void updateName(String userId, String name) {
        db.update("UPDATE users SET name = ? WHERE id = ?", List.of(name, userId));
    }

    private User insert(User user) {
        // ArrayList (not List.of) — nullable columns must pass null parameters through
        List<Object> params = new java.util.ArrayList<>();
        params.add(user.id());
        params.add(user.phone());
        params.add(user.email());
        params.add(user.name());
        params.add(user.avatarUrl());
        params.add(System.currentTimeMillis());
        db.update(
                "INSERT INTO users (id, phone, email, name, avatar_url, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                params
        );
        return user;
    }

    static User map(Map<String, Object> row) {
        return new User(
                (String) row.get("id"),
                (String) row.get("phone"),
                (String) row.get("email"),
                (String) row.get("name"),
                (String) row.get("avatar_url")
        );
    }
}
