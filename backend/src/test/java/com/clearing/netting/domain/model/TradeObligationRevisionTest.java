package com.clearing.netting.domain.model;

import com.clearing.netting.domain.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TradeObligationRevisionTest {

    private TradeObligation open() {
        return TradeObligation.open(
                "A", "B", "USD", new BigDecimal("100"),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10));
    }

    @Test
    void revisesAmountWhenOpen() {
        TradeObligation o = open();
        BigDecimal old = o.reviseAmount(new BigDecimal("120.5"));
        assertEquals(0, old.compareTo(new BigDecimal("100.00000000")));
        assertEquals(0, o.getAmount().compareTo(new BigDecimal("120.50000000")));
        assertEquals(ObligationStatus.OPEN, o.getStatus());
    }

    @Test
    void rejectsNonPositiveAmount() {
        TradeObligation o = open();
        DomainException zero = assertThrows(DomainException.class,
                () -> o.reviseAmount(BigDecimal.ZERO));
        assertEquals("INVALID_AMOUNT", zero.getCode());
        DomainException negative = assertThrows(DomainException.class,
                () -> o.reviseAmount(new BigDecimal("-1")));
        assertEquals("INVALID_AMOUNT", negative.getCode());
        assertThrows(DomainException.class, () -> o.reviseAmount(null));
        // amount unchanged after a rejected revision
        assertEquals(0, o.getAmount().compareTo(new BigDecimal("100.00000000")));
    }

    @Test
    void rejectsNettedSettledCancelled() {
        for (ObligationStatus status : new ObligationStatus[]{
                ObligationStatus.NETTED, ObligationStatus.SETTLED, ObligationStatus.CANCELLED}) {
            TradeObligation o = new TradeObligation(
                    "o", "A", "B", "USD", new BigDecimal("100"),
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10),
                    status, status == ObligationStatus.NETTED ? "run-1" : null);
            DomainException ex = assertThrows(DomainException.class,
                    () -> o.reviseAmount(new BigDecimal("200")));
            assertEquals("INVALID_STATE", ex.getCode());
            assertEquals(0, o.getAmount().compareTo(new BigDecimal("100.00000000")));
        }
    }
}
