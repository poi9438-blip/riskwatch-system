package com.riskwatch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskwatch.common.BusinessException;
import com.riskwatch.common.TraceIds;
import com.riskwatch.domain.SourceSystem;
import com.riskwatch.dto.evaluation.EvaluationRequest;
import com.riskwatch.dto.evaluation.EvaluationView;
import com.riskwatch.entity.RiskEvaluation;
import com.riskwatch.repository.CriteriaVersionRepository;
import com.riskwatch.repository.RiskEvaluationRepository;
import com.riskwatch.rule.EvaluationInput;
import com.riskwatch.rule.EvaluationOutcome;
import com.riskwatch.rule.RuleEngine;
import com.riskwatch.rule.RuleReason;
import com.riskwatch.security.AuthPrincipal;
import com.riskwatch.security.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

// 평가: 적용 중인 기준 버전으로 규칙 엔진을 돌리고 입력·판정·근거·기준 버전을 기록 (업무 명세 6장), 평가 API·조회 (7장)
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final CriteriaService criteriaService;
    private final RuleEngine ruleEngine;
    private final RiskEvaluationRepository evaluationRepository;
    private final CriteriaVersionRepository versionRepository;
    private final ObjectMapper objectMapper;

    private static final TypeReference<List<RuleReason>> REASONS = new TypeReference<>() {
    };

    // 평가 결과 (외부 평가 번호, 기준 버전 이름, 시각 포함)
    public record Result(String evaluationId, String criteriaVersion, EvaluationOutcome outcome,
            LocalDateTime createdAt) {
    }

    // 평가 API: 요청 서버는 토큰의 발급자 (본문 값을 받지 않음)
    @Transactional
    public EvaluationView evaluate(EvaluationRequest request, AuthPrincipal caller) {
        EvaluationInput input = request.toInput();
        Result result = evaluate(input, caller.sourceSystem());
        EvaluationOutcome outcome = result.outcome();
        return new EvaluationView(result.evaluationId(), caller.sourceSystem(), input.sourceRef(),
                input.applicantRef(), input.productCode(), outcome.verdict(), outcome.riskLevel(),
                outcome.riskScore(), outcome.recommendation(), result.criteriaVersion(), outcome.reasons(),
                outcome.notEvaluated(), result.createdAt());
    }

    // 평가 조회: 서비스는 자기가 요청한 평가만, 운영자는 전부
    // 다른 서버의 평가는 없는 평가와 같은 404 (있는지 알려 주지 않음)
    @Transactional(readOnly = true)
    public EvaluationView find(String evaluationId, AuthPrincipal caller) {
        RiskEvaluation e = evaluationRepository.findByEvaluationId(evaluationId)
                .filter(found -> caller.role() == Role.ADMIN || found.getSourceSystem() == caller.sourceSystem())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Evaluation not found"));
        String version = versionRepository.findById(e.getCriteriaVersionId())
                .orElseThrow(() -> new IllegalStateException("Missing criteria version " + e.getCriteriaVersionId()))
                .getVersion();
        return new EvaluationView(e.getEvaluationId(), e.getSourceSystem(), e.getSourceRef(), e.getApplicantRef(),
                e.getProductCode(), e.getVerdict(), e.getRiskLevel(), e.getRiskScore(), e.getRecommendation(),
                version, readReasons(e.getReasons()), RuleEngine.notEvaluated(e.getProductCode()), e.getCreatedAt());
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

    private List<RuleReason> readReasons(String json) {
        try {
            return objectMapper.readValue(json, REASONS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read evaluation reasons", e);
        }
    }
}
