package com.riskwatch.repository;

import com.riskwatch.domain.ProductCode;
import com.riskwatch.domain.Verdict;
import com.riskwatch.entity.RiskEvaluation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// 평가 기록 저장/조회
public interface RiskEvaluationRepository extends JpaRepository<RiskEvaluation, Long> {

    Optional<RiskEvaluation> findByEvaluationId(String evaluationId);

    // 운영자 목록: 상품·판정 필터(없으면 전체), 최신순 (같은 시각이면 id 역순)
    @Query("select e from RiskEvaluation e"
            + " where (:productCode is null or e.productCode = :productCode)"
            + " and (:verdict is null or e.verdict = :verdict)"
            + " order by e.createdAt desc, e.id desc")
    List<RiskEvaluation> search(@Param("productCode") ProductCode productCode, @Param("verdict") Verdict verdict,
            Pageable pageable);
}
