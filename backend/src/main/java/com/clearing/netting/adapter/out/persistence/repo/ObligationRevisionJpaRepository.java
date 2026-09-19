package com.clearing.netting.adapter.out.persistence.repo;

import com.clearing.netting.adapter.out.persistence.entity.ObligationRevisionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ObligationRevisionJpaRepository extends JpaRepository<ObligationRevisionJpaEntity, String> {

    List<ObligationRevisionJpaEntity> findByObligationIdOrderByRevisedAtDesc(String obligationId);
}
