package rs.unit.backend.deposit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import rs.unit.backend.deposit.domain.BanknotePolicy.Outcome;

class BanknotePolicyTest {

    @Test
    void acceptsEverySupportedEuroDenomination() {
        for (int denomination : new int[] {5, 10, 20, 50, 100, 200, 500}) {
            assertEquals(Outcome.ACCEPTED, BanknotePolicy.evaluate("EUR", denomination));
        }
    }

    @Test
    void rejectsOtherCurrencies() {
        for (String currency : new String[] {"USD", "RSD", "GBP"}) {
            assertEquals(Outcome.CURRENCY_REJECTED, BanknotePolicy.evaluate(currency, 20));
        }
    }

    @Test
    void rejectsUnsupportedEuroDenominations() {
        for (int denomination : new int[] {1, 2, 3, 25, 1000}) {
            assertEquals(
                    Outcome.DENOMINATION_REJECTED, BanknotePolicy.evaluate("EUR", denomination));
        }
    }
}
