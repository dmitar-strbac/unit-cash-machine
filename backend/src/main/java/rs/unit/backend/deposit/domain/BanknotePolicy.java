package rs.unit.backend.deposit.domain;

import java.util.Set;

public final class BanknotePolicy {

    private static final Set<Integer> EUR_DENOMINATIONS = Set.of(5, 10, 20, 50, 100, 200, 500);

    private BanknotePolicy() {}

    public static Outcome evaluate(String currency, int denomination) {
        if (!"EUR".equals(currency)) {
            return Outcome.CURRENCY_REJECTED;
        }

        if (!EUR_DENOMINATIONS.contains(denomination)) {
            return Outcome.DENOMINATION_REJECTED;
        }

        return Outcome.ACCEPTED;
    }

    public enum Outcome {
        ACCEPTED,
        CURRENCY_REJECTED,
        DENOMINATION_REJECTED
    }
}
