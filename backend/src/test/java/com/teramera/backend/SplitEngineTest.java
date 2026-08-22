package com.teramera.backend;

import com.teramera.backend.group.SplitEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplitEngineTest {

    @Test
    void equalSplitDistributesRemainderPaise() {
        var result = SplitEngine.compute(new SplitEngine.Input(
                SplitEngine.SplitType.EQUAL, 100_001L, List.of("a", "b", "c"), Map.of()));
        var shares = assertInstanceOf(SplitEngine.Result.Ok.class, result).shares();
        assertEquals(100_001L, shares.stream().mapToLong(SplitEngine.Share::amountMinor).sum());
        assertEquals(33_334L, shares.get(0).amountMinor());
        assertEquals(33_333L, shares.get(2).amountMinor());
    }

    @Test
    void exactSplitRejectsWrongSum() {
        var result = SplitEngine.compute(new SplitEngine.Input(
                SplitEngine.SplitType.EXACT, 1_000L, List.of("a", "b"), Map.of("a", 600L)));
        assertInstanceOf(SplitEngine.Result.Invalid.class, result);
    }

    @Test
    void percentSplitRequiresHundred() {
        var bad = SplitEngine.compute(new SplitEngine.Input(
                SplitEngine.SplitType.PERCENT, 1_000L, List.of("a", "b"), Map.of("a", 40L, "b", 50L)));
        assertInstanceOf(SplitEngine.Result.Invalid.class, bad);

        var good = SplitEngine.compute(new SplitEngine.Input(
                SplitEngine.SplitType.PERCENT, 10_001L, List.of("a", "b"), Map.of("a", 50L, "b", 50L)));
        var shares = assertInstanceOf(SplitEngine.Result.Ok.class, good).shares();
        assertEquals(10_001L, shares.stream().mapToLong(SplitEngine.Share::amountMinor).sum());
    }

    @Test
    void shareSplitIsProportional() {
        var result = SplitEngine.compute(new SplitEngine.Input(
                SplitEngine.SplitType.SHARES, 10_000L, List.of("a", "b", "c"),
                Map.of("a", 1L, "b", 1L, "c", 2L)));
        var shares = assertInstanceOf(SplitEngine.Result.Ok.class, result).shares();
        assertEquals(2_500L, byUser(shares, "a"));
        assertEquals(5_000L, byUser(shares, "c"));
    }

    private long byUser(List<SplitEngine.Share> shares, String userId) {
        return shares.stream().filter(s -> s.userId().equals(userId)).findFirst().orElseThrow().amountMinor();
    }
}
