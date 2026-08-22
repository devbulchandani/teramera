package com.teramera.backend.group;

import com.teramera.backend.db.SqlExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class GroupRepository {

    private final SqlExecutor db;

    public GroupRepository(SqlExecutor db) {
        this.db = db;
    }

    public record Group(String id, String name, String currency, String createdBy, long createdAt) {}

    public Group create(String name, String currency, String createdBy) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        db.update("INSERT INTO groups (id, name, currency, created_by, created_at) VALUES (?, ?, ?, ?, ?)",
                List.of(id, name, currency == null ? "INR" : currency, createdBy, now));
        addMember(id, createdBy);
        return new Group(id, name, currency == null ? "INR" : currency, createdBy, now);
    }

    public void addMember(String groupId, String userId) {
        db.update("INSERT OR IGNORE INTO memberships (group_id, user_id, role) VALUES (?, ?, 'member')",
                List.of(groupId, userId));
    }

    public boolean isMember(String groupId, String userId) {
        var rows = db.query(
                "SELECT 1 AS m FROM memberships WHERE group_id = ? AND user_id = ?",
                List.of(groupId, userId));
        return !rows.isEmpty();
    }

    public List<String> memberIds(String groupId) {
        return db.query("SELECT user_id FROM memberships WHERE group_id = ?", List.of(groupId))
                .stream().map(r -> (String) r.get("user_id")).toList();
    }

    public Optional<Group> byId(String groupId) {
        var rows = db.query("SELECT * FROM groups WHERE id = ?", List.of(groupId));
        if (rows.isEmpty()) return Optional.empty();
        Map<String, Object> row = rows.getFirst();
        return Optional.of(new Group(
                (String) row.get("id"),
                (String) row.get("name"),
                (String) row.get("currency"),
                (String) row.get("created_by"),
                ((Number) row.get("created_at")).longValue()));
    }

    public List<Group> forUser(String userId) {
        return db.query("""
                        SELECT g.* FROM groups g
                        JOIN memberships m ON m.group_id = g.id
                        WHERE m.user_id = ?
                        ORDER BY g.created_at DESC
                        """, List.of(userId))
                .stream()
                .map(row -> new Group(
                        (String) row.get("id"),
                        (String) row.get("name"),
                        (String) row.get("currency"),
                        (String) row.get("created_by"),
                        ((Number) row.get("created_at")).longValue()))
                .toList();
    }
}
