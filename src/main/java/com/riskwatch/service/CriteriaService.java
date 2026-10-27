package com.riskwatch.service;

import com.riskwatch.domain.ProductCode;
import com.riskwatch.entity.CriteriaValue;
import com.riskwatch.entity.CriteriaVersion;
import com.riskwatch.repository.CriteriaValueRepository;
import com.riskwatch.repository.CriteriaVersionRepository;
import com.riskwatch.rule.Criteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

// 기준 버전 선택과 상품별 기준값 (업무 명세 5장)
@Service
@RequiredArgsConstructor
public class CriteriaService {

    private final CriteriaVersionRepository versionRepository;
    private final CriteriaValueRepository valueRepository;

    // 평가에 쓸 버전과 그 상품의 기준값
    public record Applied(CriteriaVersion version, Criteria criteria) {
    }

    // 운영자가 미리 만들어 두고 적용시간되면 사용(최신버전 적용)
    // 지금 적용 중인 버전(적용 시각 ≤ at 중 가장 최근)의 상품 기준값, 없으면 평가 거부
    @Transactional(readOnly = true)
    public Applied applied(ProductCode productCode, LocalDateTime at) {
        CriteriaVersion version = versionRepository
                .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(at)
                .orElseThrow(() -> new IllegalStateException("No criteria version in effect at " + at));
        Map<String, BigDecimal> values = valueRepository.findByVersionIdAndProductCode(version.getId(), productCode)
                .stream()
                .collect(Collectors.toMap(CriteriaValue::getCriteriaKey, CriteriaValue::getValue));
        return new Applied(version, new Criteria(version.getVersion(), productCode, values));
    }
}
