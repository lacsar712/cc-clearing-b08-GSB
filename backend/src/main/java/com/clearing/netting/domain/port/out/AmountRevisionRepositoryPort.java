package com.clearing.netting.domain.port.out;

import com.clearing.netting.domain.model.AmountRevision;

import java.util.List;

public interface AmountRevisionRepositoryPort {
    AmountRevision save(AmountRevision revision);

    List<AmountRevision> findByObligationId(String obligationId);
}
