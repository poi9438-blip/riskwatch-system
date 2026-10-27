package com.riskwatch.dto.criteria;

import com.riskwatch.domain.ProductCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// 기준 버전 응답들
public final class VersionView {

    private VersionView() {
    }

    // 상태: 지금 적용 중 / 예약(적용 시각 전) / 지난 버전
    public enum Status {
        ACTIVE, SCHEDULED, PAST
    }

    // 목록 한 줄
    public record Summary(String version, Status status, LocalDateTime effectiveFrom, String createdBy, String reason,
            LocalDateTime createdAt) {
    }

    // 이전 버전 대비 바뀐 값
    public record ValueChange(ProductCode productCode, String key, BigDecimal before, BigDecimal after) {
    }

    // 상세: 값 전체(상품 → 키 → 값) + 이전 버전 대비 바뀐 값
    public record Detail(Summary summary, Map<ProductCode, Map<String, BigDecimal>> values,
            String previousVersion, List<ValueChange> changes) {
    }
}
