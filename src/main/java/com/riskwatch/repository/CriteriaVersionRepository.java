package com.riskwatch.repository;

import com.riskwatch.entity.CriteriaVersion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// 기준 버전 저장/조회
public interface CriteriaVersionRepository extends JpaRepository<CriteriaVersion, Long> {

    // 지금 적용 중인 버전: 적용 시각 ≤ 기준 시각인 버전 중 가장 최근 (같은 시각이면 나중에 만든 것)
    Optional<CriteriaVersion> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
            LocalDateTime at);

    Optional<CriteriaVersion> findByVersion(String version);

    boolean existsByVersion(String version);

    // 예약된 버전(적용 시각이 기준 시각보다 뒤)이 있는지
    boolean existsByEffectiveFromAfter(LocalDateTime at);

    // 목록 (최신순)
    List<CriteriaVersion> findAllByOrderByEffectiveFromDescIdDesc();

    // 지금 적용 중인 버전 행 잠금 (새 버전 만들기를 한 번에 하나씩)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from CriteriaVersion v where v.id = :id")
    Optional<CriteriaVersion> lockById(@Param("id") Long id);
}
