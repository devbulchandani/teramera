package com.teramera.backend.group;

import com.teramera.backend.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Balance computation + greedy debt simplification, scoped to a group or to
 * everything involving a user. Mirrors the Android app's logic.
 */
@Service
public class LedgerService {

    public record Balance(String userId, long netMinor) {}
    public record Transfer(String fromUserId, String toUserId, long amountMinor) {}
    public record Simplified(List<Transfer> transfers, int worstCaseCount) {}

    private final ExpenseRepository expenses;
    private final SettlementRepository settlements;
    private final UserRepository users;

    public LedgerService(ExpenseRepository expenses, SettlementRepository settlements, UserRepository users) {
        this.expenses = expenses;
        this.settlements = settlements;
        this.users = users;
    }

    /**
     * Per-member net within one group: what each member is owed (positive) or owes (negative).
     */
    public List<Balance> groupBalances(String groupId) {
        var groupExpenses = expenses.byGroup(groupId);
        var sharesByExpense = expenses.sharesByExpense(groupExpenses);
        var groupSettlements = settlements.byGroup(groupId);

        Map<String, Long> paidMinusOwes = new HashMap<>();
        for (var expense : groupExpenses) {
            paidMinusOwes.merge(expense.paidByUserId(), expense.amountMinor(), Long::sum);
            for (var share : sharesByExpense.getOrDefault(expense.id(), List.of())) {
                paidMinusOwes.merge(share.userId(), -share.shareAmountMinor(), Long::sum);
            }
        }
        for (var settlement : groupSettlements) {
            // A payment from P to R reduces P's debt → P's overpaid-net rises by the amount,
            // and R's drops by the same (they are now owed less).
            paidMinusOwes.merge(settlement.payerUserId(), settlement.amountMinor(), Long::sum);
            paidMinusOwes.merge(settlement.paidToUserId(), -settlement.amountMinor(), Long::sum);
        }
        return paidMinusOwes.entrySet().stream()
                .map(e -> new Balance(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * Friend-level net vs {@code selfId} across all expenses and settlements.
     * Positive → that friend owes self.
     */
    public List<Balance> friendBalances(String selfId) {
        Map<String, Long> net = new HashMap<>();

        for (var expense : expenses.involvingUser(selfId)) {
            var shares = expenses.sharesByExpense(List.of(expense)).getOrDefault(expense.id(), List.of());
            for (var share : shares) {
                if (expense.paidByUserId().equals(selfId) && !share.userId().equals(selfId)) {
                    net.merge(share.userId(), share.shareAmountMinor(), Long::sum);
                } else if (!expense.paidByUserId().equals(selfId) && share.userId().equals(selfId)) {
                    net.merge(expense.paidByUserId(), -share.shareAmountMinor(), Long::sum);
                }
            }
        }

        for (var s : settlements.involvingUser(selfId)) {
            if (!s.payerUserId().equals(selfId) && s.paidToUserId().equals(selfId)) {
                net.merge(s.payerUserId(), -s.amountMinor(), Long::sum);
            } else if (s.payerUserId().equals(selfId) && !s.paidToUserId().equals(selfId)) {
                net.merge(s.paidToUserId(), s.amountMinor(), Long::sum);
            }
        }

        return net.entrySet().stream()
                .filter(e -> e.getValue() != 0 || users.byId(e.getKey()).isPresent())
                .map(e -> new Balance(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * Greedy simplification: repeatedly match largest debtor with largest creditor.
     * Produces at most (debtors + creditors − 1) transfers and preserves all nets.
     */
    public Simplified simplify(List<Balance> balances) {
        record Account(String userId, long amount) {}

        var creditors = new ArrayList<Account>();
        var debtors = new ArrayList<Account>();
        for (var balance : balances) {
            if (balance.netMinor() > 0) creditors.add(new Account(balance.userId(), balance.netMinor()));
            else if (balance.netMinor() < 0) debtors.add(new Account(balance.userId(), -balance.netMinor()));
        }
        int worstCase = creditors.size() * debtors.size();

        List<Transfer> transfers = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            creditors.sort((a, b) -> Long.compare(b.amount(), a.amount()));
            debtors.sort((a, b) -> Long.compare(b.amount(), a.amount()));
            var creditor = creditors.getFirst();
            var debtor = debtors.getFirst();
            long amount = Math.min(creditor.amount(), debtor.amount());
            transfers.add(new Transfer(debtor.userId(), creditor.userId(), amount));
            if (creditor.amount() - amount <= 0) creditors.removeFirst();
            else creditors.set(0, new Account(creditor.userId(), creditor.amount() - amount));
            if (debtor.amount() - amount <= 0) debtors.removeFirst();
            else debtors.set(0, new Account(debtor.userId(), debtor.amount() - amount));
        }
        return new Simplified(transfers, worstCase);
    }
}
