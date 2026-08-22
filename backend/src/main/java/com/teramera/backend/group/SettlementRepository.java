package com.teramera.backend.group;

import com.teramera.backend.db.SqlExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class SettlementRepository {

    private final SqlExecutor db;

    public SettlementRepository(SqlExecutor db) {
        this.db = db;
    }

    public record Settlement(
            String id, String groupId, String payerUserId, String paidToUserId,
            long amountMinor, String method, long createdAt) {}

    public void insert(Settlement settlement) {
        String id = UUID.randomUUID().toString();
        List<Object> params = new java.util.ArrayList<>();
        params.add(id);
        params.add(settlement.groupId()); // nullable for direct settlements
        params.add(settlement.payerUserId());
        params.add(settlement.paidToUserId());
        params.add(settlement.amountMinor());
        params.add(settlement.method());
        params.add(settlement.createdAt());
        db.update("""
                        INSERT INTO settlements (id, group_id, payer_user_id, paid_to_user_id, amount_minor, method, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                params);
    }

    public List<Settlement> byGroup(String groupId) {
        return db.query("SELECT * FROM settlements WHERE group_id = ? ORDER BY created_at DESC", List.of(groupId))
                .stream().map(SettlementRepository::map).toList();
    }

    public List<Settlement> involvingUser(String userId) {
        return db.query(
                        "SELECT * FROM settlements WHERE payer_user_id = ? OR paid_to_user_id = ? ORDER BY created_at DESC",
                        List.of(userId, userId))
                .stream().map(SettlementRepository::map).toList();
    }

    private static Settlement map(Map<String, Object> row) {
        return new Settlement(
                (String) row.get("id"),
                (String) row.get("group_id"),
                (String) row.get("payer_user_id"),
                (String) row.get("paid_to_user_id"),
                ((Number) row.get("amount_minor")).longValue(),
                (String) row.get("method"),
                ((Number) row.get("created_at")).longValue());
    }
}
