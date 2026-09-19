package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.AmountRevision;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.model.MemberStatus;
import com.clearing.netting.domain.model.ObligationStatus;
import com.clearing.netting.domain.model.TradeObligation;
import com.clearing.netting.domain.port.out.AmountRevisionRepositoryPort;
import com.clearing.netting.domain.port.out.MemberRepositoryPort;
import com.clearing.netting.domain.port.out.ObligationRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class ObligationApplicationService {

    private final ObligationRepositoryPort obligationRepository;
    private final MemberRepositoryPort memberRepository;
    private final AmountRevisionRepositoryPort revisionRepository;

    public ObligationApplicationService(
            ObligationRepositoryPort obligationRepository,
            MemberRepositoryPort memberRepository,
            AmountRevisionRepositoryPort revisionRepository) {
        this.obligationRepository = obligationRepository;
        this.memberRepository = memberRepository;
        this.revisionRepository = revisionRepository;
    }

    @Transactional(readOnly = true)
    public List<TradeObligation> list(String currency, LocalDate settleDate, ObligationStatus status) {
        return obligationRepository.findByFilters(currency, settleDate, status);
    }

    @Transactional
    public TradeObligation create(
            String payerMemberId,
            String payeeMemberId,
            String currency,
            BigDecimal amount,
            LocalDate tradeDate,
            LocalDate settleDate) {
        validateMember(payerMemberId);
        validateMember(payeeMemberId);
        TradeObligation obligation = TradeObligation.open(
                payerMemberId, payeeMemberId, currency, amount, tradeDate, settleDate);
        return obligationRepository.save(obligation);
    }

    /**
     * Revise the amount of an OPEN obligation and persist an audit record
     * (old value, new value, operator, timestamp) in the same transaction.
     * NETTED/SETTLED/CANCELLED obligations and non-positive amounts are rejected
     * by the domain model; authorization (operator-only) is enforced in the controller.
     */
    @Transactional
    public TradeObligation reviseAmount(String obligationId, BigDecimal newAmount, String operator) {
        if (newAmount == null || newAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("INVALID_AMOUNT", "amount must be a positive number");
        }
        TradeObligation obligation = obligationRepository.findById(obligationId)
                .orElseThrow(() -> new DomainException(
                        "OBLIGATION_NOT_FOUND", "obligation not found: " + obligationId));

        BigDecimal oldAmount = obligation.reviseAmount(newAmount);
        TradeObligation saved = obligationRepository.save(obligation);

        revisionRepository.save(AmountRevision.record(
                obligation.getObligationId(),
                oldAmount,
                saved.getAmount(),
                operator,
                Instant.now()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AmountRevision> listRevisions(String obligationId) {
        obligationRepository.findById(obligationId)
                .orElseThrow(() -> new DomainException(
                        "OBLIGATION_NOT_FOUND", "obligation not found: " + obligationId));
        return revisionRepository.findByObligationId(obligationId);
    }

    private void validateMember(String memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "member not found: " + memberId));
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            throw new DomainException("SUSPENDED_MEMBER", "cannot create obligation for suspended member: " + memberId);
        }
    }
}
