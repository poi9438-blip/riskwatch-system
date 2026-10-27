package com.riskwatch.repository;

import com.riskwatch.entity.CriteriaVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

// 기준 버전 저장/조회
public interface CriteriaVersionRepository extends JpaRepository<CriteriaVersion, Long> {

    // 지금 적용 중인 버전: 적용 시각 ≤ 기준 시각인 버전 중 가장 최근 (같은 시각이면 나중에 만든 것)
    Optional<CriteriaVersion> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
            LocalDateTime at);

    Optional<CriteriaVersion> findByVersion(String version);
}
