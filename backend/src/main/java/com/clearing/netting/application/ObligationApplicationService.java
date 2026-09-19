package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.model.MemberStatus;
import com.clearing.netting.domain.model.ObligationAmountRevision;
import com.clearing.netting.domain.model.ObligationStatus;
import com.clearing.netting.domain.model.TradeObligation;
import com.clearing.netting.domain.port.out.MemberRepositoryPort;
import com.clearing.netting.domain.port.out.ObligationRepositoryPort;
import com.clearing.netting.domain.port.out.ObligationRevisionRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ObligationApplicationService {

    private final ObligationRepositoryPort obligationRepository;
    private final ObligationRevisionRepositoryPort revisionRepository;
    private final MemberRepositoryPort memberRepository;

    public ObligationApplicationService(
            ObligationRepositoryPort obligationRepository,
            ObligationRevisionRepositoryPort revisionRepository,
            MemberRepositoryPort memberRepository) {
        this.obligationRepository = obligationRepository;
        this.revisionRepository = revisionRepository;
        this.memberRepository = memberRepository;
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

    @Transactional
    public TradeObligation reviseAmount(String obligationId, BigDecimal newAmount, String operator) {
        if (newAmount == null || newAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("INVALID_AMOUNT", "amount must be a positive number");
        }
        TradeObligation obligation = obligationRepository.findById(obligationId)
                .orElseThrow(() -> new DomainException("OBLIGATION_NOT_FOUND", "obligation not found: " + obligationId));
        if (obligation.getStatus() != ObligationStatus.OPEN) {
            throw new DomainException("INVALID_STATE",
                    "only OPEN obligations can be revised, current status: " + obligation.getStatus());
        }
        BigDecimal oldAmount = obligation.getAmount();
        obligation.reviseAmount(newAmount);
        TradeObligation saved = obligationRepository.save(obligation);
        revisionRepository.save(ObligationAmountRevision.of(obligationId, oldAmount, saved.getAmount(), operator));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ObligationAmountRevision> listRevisions(String obligationId) {
        obligationRepository.findById(obligationId)
                .orElseThrow(() -> new DomainException("OBLIGATION_NOT_FOUND", "obligation not found: " + obligationId));
        return revisionRepository.findByObligationIdOrderByRevisedAtDesc(obligationId);
    }

    private void validateMember(String memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "member not found: " + memberId));
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            throw new DomainException("SUSPENDED_MEMBER", "cannot create obligation for suspended member: " + memberId);
        }
    }
}
