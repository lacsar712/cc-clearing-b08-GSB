package com.clearing.netting.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "amount_revisions", indexes = {
        @Index(name = "idx_amount_revision_obligation", columnList = "obligationId")
})
public class AmountRevisionJpaEntity {

    @Id
    @Column(length = 64)
    private String revisionId;

    @Column(nullable = false, length = 64)
    private String obligationId;

    @Column(nullable = false, precision = 28, scale = 8)
    private BigDecimal oldAmount;

    @Column(nullable = false, precision = 28, scale = 8)
    private BigDecimal newAmount;

    @Column(nullable = false, length = 64)
    private String operator;

    @Column(nullable = false)
    private Instant revisedAt;

    public String getRevisionId() {
        return revisionId;
    }

    public void setRevisionId(String revisionId) {
        this.revisionId = revisionId;
    }

    public String getObligationId() {
        return obligationId;
    }

    public void setObligationId(String obligationId) {
        this.obligationId = obligationId;
    }

    public BigDecimal getOldAmount() {
        return oldAmount;
    }

    public void setOldAmount(BigDecimal oldAmount) {
        this.oldAmount = oldAmount;
    }

    public BigDecimal getNewAmount() {
        return newAmount;
    }

    public void setNewAmount(BigDecimal newAmount) {
        this.newAmount = newAmount;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Instant getRevisedAt() {
        return revisedAt;
    }

    public void setRevisedAt(Instant revisedAt) {
        this.revisedAt = revisedAt;
    }
}
