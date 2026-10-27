package com.riskwatch.repository;

import com.riskwatch.entity.RiskEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// 평가 기록 저장/조회
public interface RiskEvaluationRepository extends JpaRepository<RiskEvaluation, Long> {

    Optional<RiskEvaluation> findByEvaluationId(String evaluationId);
}
