package com.riskwatch.rule;

import com.riskwatch.domain.ProductCode;
import com.riskwatch.support.SpecCriteria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// 기준 값 검증 규칙 단위 테스트
class CriteriaRulesTest {

    private static boolean ok(ProductCode product, String key, String value) {
        return CriteriaRules.invalid(product, key, new BigDecimal(value)).isEmpty();
    }

    @Test
    @DisplayName("[TC-CRIT-201] 값 검증: 금액 1 이상 정수, 비율 0 초과 1 이하(소수 넷째 자리), 기간 12~600 정수, 상품별 키는 명세 5장 표의 칸")
    void rules() {
        // 상품별 키 = 명세 5장 표
        for (ProductCode product : ProductCode.values()) {
            assertThat(CriteriaRules.KEYS.get(product)).as(product.name())
                    .containsExactlyInAnyOrderElementsOf(SpecCriteria.VALUES.get(product).keySet());
        }
        assertThat(ok(ProductCode.DIDIMDOL, "INCOME_LIMIT_ONE_CHILD", "90000000")).isFalse();

        // 금액
        assertThat(ok(ProductCode.DIDIMDOL, "INCOME_LIMIT", "1")).isTrue();
        assertThat(ok(ProductCode.DIDIMDOL, "INCOME_LIMIT", "60000000.00")).isTrue();
        assertThat(ok(ProductCode.DIDIMDOL, "INCOME_LIMIT", "0")).isFalse();
        assertThat(ok(ProductCode.DIDIMDOL, "INCOME_LIMIT", "60000000.5")).isFalse();
        assertThat(ok(ProductCode.DIDIMDOL, "INCOME_LIMIT", "-1")).isFalse();
        // 비율
        assertThat(ok(ProductCode.DIDIMDOL, "LTV", "1")).isTrue();
        assertThat(ok(ProductCode.DIDIMDOL, "DTI_LIMIT", "0.0001")).isTrue();
        assertThat(ok(ProductCode.DIDIMDOL, "LTV", "0")).isFalse();
        assertThat(ok(ProductCode.DIDIMDOL, "LTV", "1.0001")).isFalse();
        assertThat(ok(ProductCode.DIDIMDOL, "DTI_LIMIT", "0.60001")).isFalse();
        assertThat(ok(ProductCode.DIDIMDOL, "DTI_LIMIT", "60")).isFalse();
        // 기간
        assertThat(ok(ProductCode.BOGEUMJARI, "MAX_TERM_MONTHS", "12")).isTrue();
        assertThat(ok(ProductCode.BOGEUMJARI, "MAX_TERM_MONTHS", "600")).isTrue();
        assertThat(ok(ProductCode.BOGEUMJARI, "MAX_TERM_MONTHS", "11")).isFalse();
        assertThat(ok(ProductCode.BOGEUMJARI, "MAX_TERM_MONTHS", "601")).isFalse();
        assertThat(ok(ProductCode.BOGEUMJARI, "MAX_TERM_MONTHS", "360.5")).isFalse();
    }
}
