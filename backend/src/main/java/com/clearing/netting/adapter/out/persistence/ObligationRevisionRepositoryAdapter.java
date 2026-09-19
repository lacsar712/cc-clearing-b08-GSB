package com.clearing.netting.adapter.out.persistence;

import com.clearing.netting.adapter.out.persistence.entity.ObligationRevisionJpaEntity;
import com.clearing.netting.adapter.out.persistence.repo.ObligationRevisionJpaRepository;
import com.clearing.netting.domain.model.ObligationAmountRevision;
import com.clearing.netting.domain.port.out.ObligationRevisionRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ObligationRevisionRepositoryAdapter implements ObligationRevisionRepositoryPort {

    private final ObligationRevisionJpaRepository repository;

    public ObligationRevisionRepositoryAdapter(ObligationRevisionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public ObligationAmountRevision save(ObligationAmountRevision revision) {
        return PersistenceMapper.toDomain(repository.save(PersistenceMapper.toEntity(revision)));
    }

    @Override
    public List<ObligationAmountRevision> findByObligationIdOrderByRevisedAtDesc(String obligationId) {
        return repository.findByObligationIdOrderByRevisedAtDesc(obligationId).stream()
                .map(PersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }
}
