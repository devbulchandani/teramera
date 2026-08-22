/**
 * Ledger math — port of the Kotlin/Java split engine and debt simplifier.
 * Amounts are paise.
 */

export type SplitType = "EQUAL" | "EXACT" | "PERCENT" | "SHARES";

export interface SplitInput {
    type: SplitType;
    totalMinor: number;
    participants: string[];
    rawValues: Record<string, number>;
}

export type SplitResult =
    | { ok: true; shares: { userId: string; amountMinor: number }[] }
    | { ok: false; reason: string };

export function computeSplit(input: SplitInput): SplitResult {
    const people = [...new Set(input.participants)];
    switch (input.type) {
        case "EQUAL": {
            const per = Math.floor(input.totalMinor / people.length);
            let remainder = input.totalMinor - per * people.length;
            return {
                ok: true,
                shares: people.map((userId) => ({
                    userId,
                    amountMinor: remainder-- > 0 ? per + 1 : per,
                })),
            };
        }
        case "EXACT": {
            const sum = people.reduce((acc, p) => acc + (input.rawValues[p] ?? 0), 0);
            if (sum !== input.totalMinor) return { ok: false, reason: "Exact amounts do not sum to the total" };
            return { ok: true, shares: people.map((p) => ({ userId: p, amountMinor: input.rawValues[p] ?? 0 })) };
        }
        case "PERCENT": {
            const totalPct = people.reduce((acc, p) => acc + (input.rawValues[p] ?? 0), 0);
            if (totalPct !== 100) return { ok: false, reason: "Percentages must add up to 100" };
            const computed = people.map((p) => ({
                userId: p,
                amountMinor: Math.floor((input.totalMinor * (input.rawValues[p] ?? 0)) / 100),
            }));
            return { ok: true, shares: distributeRemainder(computed, input.totalMinor) };
        }
        case "SHARES": {
            const weight = (p: string) => Math.max(1, input.rawValues[p] ?? 1);
            const weightSum = people.reduce((acc, p) => acc + weight(p), 0);
            const computed = people.map((p) => ({
                userId: p,
                amountMinor: Math.floor((input.totalMinor * weight(p)) / weightSum),
            }));
            return { ok: true, shares: distributeRemainder(computed, input.totalMinor) };
        }
    }
}

function distributeRemainder(
    computed: { userId: string; amountMinor: number }[],
    totalMinor: number,
): { userId: string; amountMinor: number }[] {
    let remainder = totalMinor - computed.reduce((acc, s) => acc + s.amountMinor, 0);
    return computed.map((s) => {
        const extra = remainder > 0 ? (--remainder, 1) : 0;
        return { userId: s.userId, amountMinor: s.amountMinor + extra };
    });
}

/** Greedy simplification: largest debtor ↔ largest creditor until nets are clear. */
export function simplifyDebts(net: Map<string, number>): {
    transfers: { fromUserId: string; toUserId: string; amountMinor: number }[];
    worstCaseCount: number;
} {
    const creditors = [...net.entries()].filter(([, v]) => v > 0).map(([userId, amount]) => ({ userId, amount }));
    const debtors = [...net.entries()].filter(([, v]) => v < 0).map(([userId, amount]) => ({ userId, amount: -amount }));
    const worstCaseCount = creditors.length * debtors.length;

    const transfers: { fromUserId: string; toUserId: string; amountMinor: number }[] = [];
    while (creditors.length > 0 && debtors.length > 0) {
        creditors.sort((a, b) => b.amount - a.amount);
        debtors.sort((a, b) => b.amount - a.amount);
        const creditor = creditors[0];
        const debtor = debtors[0];
        const amount = Math.min(creditor.amount, debtor.amount);
        transfers.push({ fromUserId: debtor.userId, toUserId: creditor.userId, amountMinor: amount });
        creditor.amount -= amount;
        debtor.amount -= amount;
        if (creditor.amount <= 0) creditors.shift();
        if (debtor.amount <= 0) debtors.shift();
    }
    return { transfers, worstCaseCount };
}
