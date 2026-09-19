package com.clearing.netting.adapter.out.persistence;

import com.clearing.netting.adapter.out.persistence.entity.AmountRevisionJpaEntity;
import com.clearing.netting.adapter.out.persistence.repo.AmountRevisionJpaRepository;
import com.clearing.netting.domain.model.AmountRevision;
import com.clearing.netting.domain.port.out.AmountRevisionRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class AmountRevisionRepositoryAdapter implements AmountRevisionRepositoryPort {

    private final AmountRevisionJpaRepository repository;

    public AmountRevisionRepositoryAdapter(AmountRevisionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public AmountRevision save(AmountRevision revision) {
        return PersistenceMapper.toDomain(repository.save(PersistenceMapper.toEntity(revision)));
    }

    @Override
    public List<AmountRevision> findByObligationId(String obligationId) {
        return repository.findByObligationIdOrderByRevisedAtDesc(obligationId).stream()
                .map(PersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }
}
