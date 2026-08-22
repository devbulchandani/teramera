package com.teramera.backend.group;

import com.teramera.backend.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
public class ExpenseController {

    public record CreateExpenseRequest(
            String groupId, // null → direct expense between payer and participants
            @NotBlank String title,
            @NotNull Long amountMinor,
            @NotBlank String paidByUserId,
            @NotNull SplitEngine.SplitType splitType,
            @NotNull Map<String, Long> participants, // userId → raw value (ignored for EQUAL)
            String currency,
            Double fxRateToGroup) {}

    private final GroupRepository groups;
    private final UserRepository users;
    private final ExpenseRepository expenses;
    private final LedgerService ledger;

    public ExpenseController(GroupRepository groups, UserRepository users,
                             ExpenseRepository expenses, LedgerService ledger) {
        this.groups = groups;
        this.users = users;
        this.expenses = expenses;
        this.ledger = ledger;
    }

    @PostMapping("/expenses")
    public Map<String, Object> create(@Valid @RequestBody CreateExpenseRequest request, Authentication auth) {
        if (request.amountMinor() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive");
        }
        if (request.groupId() != null) {
            requireMember(request.groupId(), auth.getName());
        }
        if (users.byId(request.paidByUserId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown payer");
        }

        // EQUAL with no explicit participants → everyone in the group (how the app sends it)
        List<String> participants;
        if (request.participants().isEmpty() && request.groupId() != null) {
            participants = new java.util.ArrayList<>(groups.memberIds(request.groupId()));
        } else {
            participants = new java.util.ArrayList<>(request.participants().keySet());
            participants.add(request.paidByUserId());
            if (participants.size() < 2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least two people required");
            }
        }
        for (String participant : request.participants().keySet()) {
            if (users.byId(participant).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown participant: " + participant);
            }
        }

        var result = SplitEngine.compute(new SplitEngine.Input(
                request.splitType(), request.amountMinor(), participants, request.participants()));
        if (result instanceof SplitEngine.Result.Invalid invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.reason());
        }
        List<SplitEngine.Share> shares = ((SplitEngine.Result.Ok) result).shares();

        var expense = new ExpenseRepository.Expense(
                null, request.groupId(), request.paidByUserId(), request.title().trim(),
                shares.stream().mapToLong(SplitEngine.Share::amountMinor).sum(),
                request.splitType().name(),
                request.currency() == null ? "INR" : request.currency(),
                request.fxRateToGroup() == null ? 1.0 : request.fxRateToGroup(),
                System.currentTimeMillis());
        String id = expenses.insert(expense);
        expenses.insertShares(id, shares.stream()
                .map(share -> new ExpenseRepository.ShareRow(share.userId(), share.amountMinor()))
                .toList());

        return Map.of("id", id, "amountMinor", expense.amountMinor(), "shareCount", shares.size());
    }

    @GetMapping("/groups/{groupId}/expenses")
    public List<Map<String, Object>> byGroup(@PathVariable String groupId, Authentication auth) {
        requireMember(groupId, auth.getName());
        var groupExpenses = expenses.byGroup(groupId);
        var sharesByExpense = expenses.sharesByExpense(groupExpenses);
        return groupExpenses.stream().map(expense -> Map.<String, Object>of(
                "id", expense.id(),
                "title", expense.title(),
                "paidByUserId", expense.paidByUserId(),
                "amountMinor", expense.amountMinor(),
                "myShareMinor", sharesByExpense.getOrDefault(expense.id(), List.of()).stream()
                        .filter(s -> s.userId().equals(auth.getName()))
                        .findFirst().map(ExpenseRepository.ShareRow::shareAmountMinor).orElse(0L),
                "createdAt", expense.createdAt())).toList();
    }

    /** Friend-level balances for the home screen: everyone's net vs the caller. */
    @GetMapping("/balances")
    public List<Map<String, Object>> myBalances(Authentication auth) {
        return ledger.friendBalances(auth.getName()).stream()
                .map(b -> {
                    var user = users.byId(b.userId()).orElse(null);
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("userId", b.userId());
                    map.put("netMinor", b.netMinor());
                    map.put("name", user == null ? "?" : user.name());
                    return map;
                })
                .toList();
    }

    private void requireMember(String groupId, String userId) {
        if (!groups.isMember(groupId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this group");
        }
    }
}
