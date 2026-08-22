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
import java.util.Set;

@RestController
@RequestMapping("/settlements")
public class SettlementController {

    public record CreateSettlementRequest(
            String groupId,
            String payerUserId, // optional; defaults to caller. May also be the payee's counterpart.
            @NotBlank String paidToUserId,
            @NotNull Long amountMinor,
            @NotBlank String method) {}

    private static final Set<String> METHODS = Set.of("UPI", "CASH", "BANK");

    private final SettlementRepository settlements;
    private final GroupRepository groups;
    private final UserRepository users;

    public SettlementController(SettlementRepository settlements, GroupRepository groups, UserRepository users) {
        this.settlements = settlements;
        this.groups = groups;
        this.users = users;
    }

    /** Records a payment between the caller and one counterparty (in either direction). */
    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody CreateSettlementRequest request, Authentication auth) {
        if (request.amountMinor() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive");
        }
        if (!METHODS.contains(request.method().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Method must be UPI, CASH or BANK");
        }
        if (users.byId(request.paidToUserId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown payee");
        }
        // The caller must be one end of the payment.
        String payer = request.payerUserId() == null || request.payerUserId().isBlank()
                ? auth.getName()
                : request.payerUserId();
        if (!payer.equals(auth.getName()) && !request.paidToUserId().equals(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You must be part of the settlement");
        }
        if (users.byId(payer).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown payer");
        }
        String groupId = request.groupId();
        if (groupId != null && !groups.isMember(groupId, auth.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this group");
        }

        settlements.insert(new SettlementRepository.Settlement(
                null, groupId, payer, request.paidToUserId(),
                request.amountMinor(), request.method().toUpperCase(), System.currentTimeMillis()));

        return Map.of("status", "recorded", "amountMinor", request.amountMinor());
    }

    @GetMapping("/groups/{groupId}")
    public List<Map<String, Object>> byGroup(@PathVariable String groupId, Authentication auth) {
        if (!groups.isMember(groupId, auth.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this group");
        }
        return settlements.byGroup(groupId).stream().map(s -> Map.<String, Object>of(
                "payerUserId", s.payerUserId(),
                "paidToUserId", s.paidToUserId(),
                "amountMinor", s.amountMinor(),
                "method", s.method(),
                "createdAt", s.createdAt())).toList();
    }
}
