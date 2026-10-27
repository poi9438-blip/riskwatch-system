package com.riskwatch.rule;

import com.riskwatch.domain.Verdict;

import java.util.List;

// 규칙 엔진 결과 (판정, HomeFin 호환 값, 규칙별 근거, 판정하지 않는 기준)
public record EvaluationOutcome(Verdict verdict, String riskLevel, int riskScore, String recommendation,
        List<RuleReason> reasons, List<String> notEvaluated) {
}
