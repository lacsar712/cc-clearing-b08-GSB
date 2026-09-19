package com.clearing.netting.adapter.out.persistence.repo;

import com.clearing.netting.adapter.out.persistence.entity.AmountRevisionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AmountRevisionJpaRepository extends JpaRepository<AmountRevisionJpaEntity, String> {

    List<AmountRevisionJpaEntity> findByObligationIdOrderByRevisedAtDesc(String obligationId);
}
