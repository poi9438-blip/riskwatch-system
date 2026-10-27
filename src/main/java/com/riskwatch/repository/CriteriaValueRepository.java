package com.riskwatch.repository;

import com.riskwatch.domain.ProductCode;
import com.riskwatch.entity.CriteriaValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// 기준값 저장/조회
public interface CriteriaValueRepository extends JpaRepository<CriteriaValue, Long> {

    List<CriteriaValue> findByVersionIdAndProductCode(Long versionId, ProductCode productCode);

    List<CriteriaValue> findByVersionId(Long versionId);
}
