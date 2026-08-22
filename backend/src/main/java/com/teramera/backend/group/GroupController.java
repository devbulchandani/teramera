package com.teramera.backend.group;

import com.teramera.backend.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/groups")
public class GroupController {

    public record CreateGroupRequest(@NotBlank String name, String currency, List<String> memberUserIds) {}

    private final GroupRepository groups;
    private final UserRepository users;
    private final ExpenseRepository expenses;
    private final SettlementRepository settlements;
    private final LedgerService ledger;

    public GroupController(GroupRepository groups, UserRepository users,
                           ExpenseRepository expenses, SettlementRepository settlements,
                           LedgerService ledger) {
        this.groups = groups;
        this.users = users;
        this.expenses = expenses;
        this.settlements = settlements;
        this.ledger = ledger;
    }

    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody CreateGroupRequest request, Authentication auth) {
        var group = groups.create(request.name(), request.currency(), auth.getName());
        if (request.memberUserIds() != null) {
            for (String memberId : request.memberUserIds()) {
                if (users.byId(memberId).isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown member: " + memberId);
                }
                groups.addMember(group.id(), memberId);
            }
        }
        return Map.of("id", group.id(), "name", group.name(), "currency", group.currency());
    }

    @GetMapping
    public List<Map<String, Object>> mine(Authentication auth) {
        return groups.forUser(auth.getName()).stream().map(group -> {
            var balances = ledger.groupBalances(group.id());
            long netForMe = balances.stream()
                    .filter(b -> b.userId().equals(auth.getName()))
                    .findFirst().map(LedgerService.Balance::netMinor).orElse(0L);
            long totalSpent = expenses.byGroup(group.id()).stream()
                    .mapToLong(ExpenseRepository.Expense::amountMinor).sum();
            return Map.<String, Object>of(
                    "id", group.id(),
                    "name", group.name(),
                    "currency", group.currency(),
                    "totalSpentMinor", totalSpent,
                    "netForMeMinor", netForMe);
        }).toList();
    }

    /** Everything the app's group screen needs in one call. */
    @GetMapping("/{groupId}/detail")
    public Map<String, Object> detail(@PathVariable String groupId, Authentication auth) {
        requireMember(groupId, auth.getName());
        var group = groups.byId(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));

        var memberIds = groups.memberIds(groupId);
        var groupExpenses = expenses.byGroup(groupId);
        var sharesByExpense = expenses.sharesByExpense(groupExpenses);

        var memberList = memberIds.stream().map(memberId -> {
            var user = users.byId(memberId).orElse(null);
            Map<String, Object> map = new HashMap<>();
            map.put("id", memberId);
            map.put("name", user == null ? "?" : user.name());
            map.put("isSelf", memberId.equals(auth.getName()));
            return map;
        }).toList();

        var expenseList = groupExpenses.stream().map(expense -> Map.<String, Object>of(
                "id", expense.id(),
                "title", expense.title(),
                "paidByUserId", expense.paidByUserId(),
                "amountMinor", expense.amountMinor(),
                "myShareMinor", sharesByExpense.getOrDefault(expense.id(), List.of()).stream()
                        .filter(s -> s.userId().equals(auth.getName()))
                        .findFirst().map(ExpenseRepository.ShareRow::shareAmountMinor).orElse(0L),
                "participantCount", sharesByExpense.getOrDefault(expense.id(), List.of()).size(),
                "createdAt", expense.createdAt())).toList();

        var balances = ledger.groupBalances(groupId);
        var simplified = ledger.simplify(balances);

        return Map.of(
                "id", group.id(),
                "name", group.name(),
                "currency", group.currency(),
                "totalSpentMinor", groupExpenses.stream().mapToLong(ExpenseRepository.Expense::amountMinor).sum(),
                "members", memberList,
                "expenses", expenseList,
                "balances", balances.stream().map(b -> Map.of("userId", b.userId(), "netMinor", b.netMinor())).toList(),
                "simplifiedDebts", simplified.transfers().stream().map(t -> Map.of(
                        "fromUserId", t.fromUserId(),
                        "toUserId", t.toUserId(),
                        "amountMinor", t.amountMinor())).toList());
    }

    private void requireMember(String groupId, String userId) {
        if (!groups.isMember(groupId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this group");
        }
    }
}
