package com.riskwatch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskwatch.common.TraceIds;
import com.riskwatch.domain.SourceSystem;
import com.riskwatch.entity.RiskEvaluation;
import com.riskwatch.repository.RiskEvaluationRepository;
import com.riskwatch.rule.EvaluationInput;
import com.riskwatch.rule.EvaluationOutcome;
import com.riskwatch.rule.RuleEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

// 평가: 적용 중인 기준 버전으로 규칙 엔진을 돌리고 입력·판정·근거·기준 버전을 기록 (업무 명세 6장)
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final CriteriaService criteriaService;
    private final RuleEngine ruleEngine;
    private final RiskEvaluationRepository evaluationRepository;
    private final ObjectMapper objectMapper;

    // 평가 결과 (외부 평가 번호, 기준 버전 이름, 시각 포함)
    public record Result(String evaluationId, String criteriaVersion, EvaluationOutcome outcome,
            LocalDateTime createdAt) {
    }

    @Transactional
    public Result evaluate(EvaluationInput input, SourceSystem sourceSystem) {
        // 시각은 DB와 같은 마이크로초 정밀도 (FinFlow #4)
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        CriteriaService.Applied applied = criteriaService.applied(input.productCode(), now);
        EvaluationOutcome outcome = ruleEngine.evaluate(input, applied.criteria());

        RiskEvaluation evaluation = evaluationRepository.save(RiskEvaluation.builder()
                .evaluationId(UUID.randomUUID().toString())
                .sourceSystem(sourceSystem)
                .sourceRef(input.sourceRef())
                .applicantRef(input.applicantRef())
                .productCode(input.productCode())
                .verdict(outcome.verdict())
                .riskLevel(outcome.riskLevel())
                .riskScore(outcome.riskScore())
                .recommendation(outcome.recommendation())
                .criteriaVersionId(applied.version().getId())
                .reasons(toJson(outcome))
                .requestedAmount(input.requestedAmount())
                .termMonths(input.termMonths())
                .housePrice(input.housePrice())
                .householdAnnualIncome(BigDecimal.valueOf(input.householdAnnualIncome()))
                .dti(input.dti())
                .houseOwnership(input.houseOwnership())
                .firstHome(input.firstHome())
                .newlywed(input.newlywed())
                .newbornWithin2Years(input.newbornWithin2Years())
                .dualIncome(input.dualIncome())
                .childCount(input.childCount())
                .region(input.region())
                .traceId(TraceIds.current())
                .createdAt(now)
                .build());
        // 로그에는 금액·소득·DTI를 남기지 않음
        log.info("Evaluated evaluationId={} product={} verdict={} criteria={}", evaluation.getEvaluationId(),
                input.productCode(), outcome.verdict(), applied.version().getVersion());
        return new Result(evaluation.getEvaluationId(), applied.version().getVersion(), outcome, now);
    }

    private String toJson(EvaluationOutcome outcome) {
        try {
            return objectMapper.writeValueAsString(outcome.reasons());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize evaluation reasons", e);
        }
    }
}
