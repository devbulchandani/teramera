package com.teramera.backend.group;

import com.teramera.backend.db.SqlExecutor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ExpenseRepository {

    private final SqlExecutor db;

    public ExpenseRepository(SqlExecutor db) {
        this.db = db;
    }

    public record Expense(
            String id, String groupId, String paidByUserId, String title,
            long amountMinor, String splitType, String currency,
            double fxRateToGroup, long createdAt) {}

    public record ShareRow(String userId, long shareAmountMinor) {}

    public String insert(Expense expense) {
        String id = UUID.randomUUID().toString();
        // ArrayList + local id: record id may be null, groupId is nullable for direct expenses
        List<Object> params = new ArrayList<>();
        params.add(id);
        params.add(expense.groupId());
        params.add(expense.paidByUserId());
        params.add(expense.title());
        params.add(expense.amountMinor());
        params.add(expense.splitType());
        params.add(expense.currency());
        params.add(expense.fxRateToGroup());
        params.add(expense.createdAt());
        db.update("""
                        INSERT INTO expenses (id, group_id, paid_by_user_id, title, amount_minor, split_type, currency, fx_rate_to_group, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                params);
        return id;
    }

    public void insertShares(String expenseId, List<ShareRow> shares) {
        for (ShareRow share : shares) {
            db.update("INSERT INTO expense_shares (expense_id, user_id, share_amount_minor) VALUES (?, ?, ?)",
                    List.of(expenseId, share.userId(), share.shareAmountMinor()));
        }
    }

    public List<Expense> byGroup(String groupId) {
        return db.query("SELECT * FROM expenses WHERE group_id = ? ORDER BY created_at DESC", List.of(groupId))
                .stream().map(ExpenseRepository::map).toList();
    }

    /** All expenses where the user has a share or is the payer — used for friend-level balances. */
    public List<Expense> involvingUser(String userId) {
        return db.query("""
                        SELECT DISTINCT e.* FROM expenses e
                        LEFT JOIN expense_shares s ON s.expense_id = e.id
                        WHERE e.paid_by_user_id = ? OR s.user_id = ?
                        ORDER BY created_at DESC
                        """, List.of(userId, userId))
                .stream().map(ExpenseRepository::map).toList();
    }

    public Map<String, List<ShareRow>> sharesByExpense(List<Expense> expenses) {
        Map<String, List<ShareRow>> result = new java.util.HashMap<>();
        if (expenses.isEmpty()) return result;
        // per-expense query keeps SQL portable across SQLite and D1
        for (Expense expense : expenses) {
            List<ShareRow> shares = db.query(
                            "SELECT user_id, share_amount_minor FROM expense_shares WHERE expense_id = ?",
                            List.of(expense.id()))
                    .stream()
                    .map(row -> new ShareRow((String) row.get("user_id"), ((Number) row.get("share_amount_minor")).longValue()))
                    .toList();
            result.put(expense.id(), shares);
        }
        return result;
    }

    private static Expense map(Map<String, Object> row) {
        return new Expense(
                (String) row.get("id"),
                (String) row.get("group_id"),
                (String) row.get("paid_by_user_id"),
                (String) row.get("title"),
                ((Number) row.get("amount_minor")).longValue(),
                (String) row.get("split_type"),
                (String) row.get("currency"),
                ((Number) row.get("fx_rate_to_group")).doubleValue(),
                ((Number) row.get("created_at")).longValue());
    }
}
