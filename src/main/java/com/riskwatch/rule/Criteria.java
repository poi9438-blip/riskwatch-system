package com.riskwatch.rule;

import com.riskwatch.domain.ProductCode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// 한 상품의 기준값 (기준 버전 하나에서 꺼낸 것)
public final class Criteria {

    // 모든 상품에 있어야 하는 기본 기준 (없으면 평가 거부, 업무 명세 5장)
    public static final List<String> REQUIRED = List.of("INCOME_LIMIT", "HOUSE_PRICE_LIMIT", "PRODUCT_LOAN_LIMIT",
            "LTV", "DTI_LIMIT", "MAX_TERM_MONTHS");

    private final String version;
    private final ProductCode productCode;
    private final Map<String, BigDecimal> values;

    // 기본 기준이 하나라도 없으면 만들지 않음 (평가 거부)
    public Criteria(String version, ProductCode productCode, Map<String, BigDecimal> values) {
        List<String> missing = REQUIRED.stream().filter(k -> !values.containsKey(k)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Criteria version " + version + " is missing " + productCode + " "
                    + missing);
        }
        this.version = version;
        this.productCode = productCode;
        this.values = Map.copyOf(values);
    }

    public String version() {
        return version;
    }

    public ProductCode productCode() {
        return productCode;
    }

    public BigDecimal required(String key) {
        return values.get(key);
    }

    // 가구 조건별 기준 (그 상품에 없는 조건이면 비어 있음)
    public Optional<BigDecimal> optional(String key) {
        return Optional.ofNullable(values.get(key));
    }
}
