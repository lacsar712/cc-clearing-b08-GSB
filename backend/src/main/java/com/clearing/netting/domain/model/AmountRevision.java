package com.clearing.netting.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable audit record of one amount revision on a {@link TradeObligation}.
 * Every successful revision must produce exactly one of these.
 */
public class AmountRevision {
    private final String revisionId;
    private final String obligationId;
    private final BigDecimal oldAmount;
    private final BigDecimal newAmount;
    private final String operator;
    private final Instant revisedAt;

    public AmountRevision(
            String revisionId,
            String obligationId,
            BigDecimal oldAmount,
            BigDecimal newAmount,
            String operator,
            Instant revisedAt) {
        this.revisionId = Objects.requireNonNull(revisionId);
        this.obligationId = Objects.requireNonNull(obligationId);
        this.oldAmount = Objects.requireNonNull(oldAmount);
        this.newAmount = Objects.requireNonNull(newAmount);
        this.operator = Objects.requireNonNull(operator);
        this.revisedAt = Objects.requireNonNull(revisedAt);
    }

    public static AmountRevision record(
            String obligationId,
            BigDecimal oldAmount,
            BigDecimal newAmount,
            String operator,
            Instant revisedAt) {
        return new AmountRevision(
                UUID.randomUUID().toString(),
                obligationId,
                oldAmount,
                newAmount,
                operator,
                revisedAt);
    }

    public String getRevisionId() {
        return revisionId;
    }

    public String getObligationId() {
        return obligationId;
    }

    public BigDecimal getOldAmount() {
        return oldAmount;
    }

    public BigDecimal getNewAmount() {
        return newAmount;
    }

    public String getOperator() {
        return operator;
    }

    public Instant getRevisedAt() {
        return revisedAt;
    }
}
