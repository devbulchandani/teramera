package com.teramera.backend.group;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Server-authoritative expense splitting. Mirrors the Android client's engine;
 * the backend recomputes shares so clients can't lie about amounts.
 */
public final class SplitEngine {

    private SplitEngine() {}

    public enum SplitType { EQUAL, EXACT, PERCENT, SHARES }

    public record Share(String userId, long amountMinor) {}

    public sealed interface Result {
        record Ok(List<Share> shares) implements Result {}
        record Invalid(String reason) implements Result {}
    }

    public record Input(
            SplitType type,
            long totalMinor,
            List<String> participants,
            Map<String, Long> rawValues // exact: paise · percent: int pct · shares: weight
    ) {}

    public static Result compute(Input input) {
        List<String> people = input.participants().stream().distinct().toList();
        return switch (input.type()) {
            case EQUAL -> ok(evenSplit(input.totalMinor(), people));
            case EXACT -> {
                long sum = people.stream().mapToLong(p -> input.rawValues().getOrDefault(p, 0L)).sum();
                if (sum != input.totalMinor()) {
                    yield new Result.Invalid("Exact amounts do not sum to the total");
                } else {
                    yield ok(people.stream().map(p -> new Share(p, input.rawValues().getOrDefault(p, 0L))).toList());
                }
            }
            case PERCENT -> {
                long totalPct = people.stream().mapToLong(p -> input.rawValues().getOrDefault(p, 0L)).sum();
                if (totalPct != 100L) {
                    yield new Result.Invalid("Percentages must add up to 100");
                } else {
                    List<Share> computed = people.stream()
                            .map(p -> new Share(p, input.totalMinor() * input.rawValues().getOrDefault(p, 0L) / 100))
                            .toList();
                    yield ok(distributeRemainder(computed, input.totalMinor()));
                }
            }
            case SHARES -> {
                long weightSum = people.stream()
                        .mapToLong(p -> Math.max(1, input.rawValues().getOrDefault(p, 1L)))
                        .sum();
                List<Share> proportional = people.stream()
                        .map(p -> new Share(p, input.totalMinor() * Math.max(1, input.rawValues().getOrDefault(p, 1L)) / weightSum))
                        .toList();
                yield ok(distributeRemainder(proportional, input.totalMinor()));
            }
        };
    }

    private static List<Share> evenSplit(long totalMinor, List<String> people) {
        long per = totalMinor / people.size();
        long remainder = totalMinor - per * people.size();
        List<Share> shares = new ArrayList<>(people.size());
        for (String person : people) {
            long extra = remainder > 0 ? 1 : 0;
            if (extra == 1) remainder--;
            shares.add(new Share(person, per + extra));
        }
        return shares;
    }

    private static List<Share> distributeRemainder(List<Share> computed, long totalMinor) {
        long remainder = totalMinor - computed.stream().mapToLong(Share::amountMinor).sum();
        List<Share> result = new ArrayList<>(computed.size());
        for (Share share : computed) {
            long extra = remainder > 0 ? 1 : 0;
            if (extra == 1) remainder--;
            result.add(new Share(share.userId(), share.amountMinor() + extra));
        }
        return result;
    }

    private static Result ok(List<Share> shares) {
        boolean negative = shares.stream().anyMatch(s -> s.amountMinor() < 0);
        return negative ? new Result.Invalid("Shares cannot be negative") : new Result.Ok(shares);
    }
}
