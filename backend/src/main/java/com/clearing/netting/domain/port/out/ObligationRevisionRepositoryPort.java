package com.clearing.netting.domain.port.out;

import com.clearing.netting.domain.model.ObligationAmountRevision;

import java.util.List;

public interface ObligationRevisionRepositoryPort {
    ObligationAmountRevision save(ObligationAmountRevision revision);

    List<ObligationAmountRevision> findByObligationIdOrderByRevisedAtDesc(String obligationId);
}
