package com.clearing.netting.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TradeObligationReviseTest {

    private final LocalDate settleDate = LocalDate.of(2026, 9, 10);

    @Test
    void revisesAmountOnOpenObligation() {
        TradeObligation o = obligation(ObligationStatus.OPEN, "100");

        o.reviseAmount(new BigDecimal("250.5"));

        assertEquals(0, o.getAmount().compareTo(new BigDecimal("250.50000000")));
        assertEquals(8, o.getAmount().scale());
    }

    @Test
    void rejectsRevisionOnNettedObligation() {
        TradeObligation o = obligation(ObligationStatus.NETTED, "100");
        assertThrows(IllegalStateException.class, () -> o.reviseAmount(new BigDecimal("200")));
        assertEquals(0, o.getAmount().compareTo(new BigDecimal("100.00000000")));
    }

    @Test
    void rejectsRevisionOnSettledObligation() {
        TradeObligation o = obligation(ObligationStatus.SETTLED, "100");
        assertThrows(IllegalStateException.class, () -> o.reviseAmount(new BigDecimal("200")));
    }

    @Test
    void rejectsRevisionOnCancelledObligation() {
        TradeObligation o = obligation(ObligationStatus.CANCELLED, "100");
        assertThrows(IllegalStateException.class, () -> o.reviseAmount(new BigDecimal("200")));
    }

    @Test
    void rejectsNonPositiveAmounts() {
        TradeObligation o = obligation(ObligationStatus.OPEN, "100");
        assertThrows(IllegalArgumentException.class, () -> o.reviseAmount(null));
        assertThrows(IllegalArgumentException.class, () -> o.reviseAmount(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> o.reviseAmount(new BigDecimal("-5")));
        // amount untouched after failed revisions
        assertEquals(0, o.getAmount().compareTo(new BigDecimal("100.00000000")));
    }

    private TradeObligation obligation(ObligationStatus status, String amount) {
        return new TradeObligation(
                UUID.randomUUID().toString(),
                "A",
                "B",
                "USD",
                new BigDecimal(amount),
                settleDate.minusDays(1),
                settleDate,
                status,
                status == ObligationStatus.OPEN ? null : "run-1");
    }
}
