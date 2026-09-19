package com.clearing.netting.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Audit record for a single amount revision of a trade obligation.
 * Immutable: old value, new value, operator and timestamp are kept for traceability.
 */
public class ObligationAmountRevision {
    private final String revisionId;
    private final String obligationId;
    private final BigDecimal oldAmount;
    private final BigDecimal newAmount;
    private final String operator;
    private final Instant revisedAt;

    public ObligationAmountRevision(
            String revisionId,
            String obligationId,
            BigDecimal oldAmount,
            BigDecimal newAmount,
            String operator,
            Instant revisedAt) {
        this.revisionId = Objects.requireNonNull(revisionId);
        this.obligationId = Objects.requireNonNull(obligationId);
        this.oldAmount = Objects.requireNonNull(oldAmount).setScale(8, RoundingMode.HALF_UP);
        this.newAmount = Objects.requireNonNull(newAmount).setScale(8, RoundingMode.HALF_UP);
        this.operator = Objects.requireNonNull(operator);
        this.revisedAt = Objects.requireNonNull(revisedAt);
    }

    public static ObligationAmountRevision of(
            String obligationId,
            BigDecimal oldAmount,
            BigDecimal newAmount,
            String operator) {
        return new ObligationAmountRevision(
                UUID.randomUUID().toString(),
                obligationId,
                oldAmount,
                newAmount,
                operator,
                Instant.now());
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
