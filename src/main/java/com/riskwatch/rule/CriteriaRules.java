package com.riskwatch.rule;

import com.riskwatch.domain.ProductCode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// 기준 값 검증 규칙 (업무 명세 5장): 상품별로 쓸 수 있는 키, 키 종류별 허용 범위
public final class CriteriaRules {

        private static final List<String> DIDIMDOL_KEYS = List.of("INCOME_LIMIT", "INCOME_LIMIT_FIRST_HOME",
                        "INCOME_LIMIT_NEWLYWED", "INCOME_LIMIT_MULTI_CHILD", "HOUSE_PRICE_LIMIT",
                        "HOUSE_PRICE_LIMIT_NEWLYWED_OR_MULTI_CHILD", "PRODUCT_LOAN_LIMIT",
                        "PRODUCT_LOAN_LIMIT_FIRST_HOME",
                        "PRODUCT_LOAN_LIMIT_NEWLYWED_OR_MULTI_CHILD", "LTV", "LTV_FIRST_HOME_NON_CAPITAL", "DTI_LIMIT",
                        "MAX_TERM_MONTHS");
        private static final List<String> NEWBORN_KEYS = List.of("INCOME_LIMIT", "INCOME_LIMIT_DUAL_INCOME",
                        "HOUSE_PRICE_LIMIT", "PRODUCT_LOAN_LIMIT", "LTV", "LTV_FIRST_HOME_NON_CAPITAL", "DTI_LIMIT",
                        "MAX_TERM_MONTHS");
        private static final List<String> BOGEUMJARI_KEYS = List.of("INCOME_LIMIT", "INCOME_LIMIT_NEWLYWED",
                        "INCOME_LIMIT_ONE_CHILD", "INCOME_LIMIT_MULTI_CHILD", "HOUSE_PRICE_LIMIT", "PRODUCT_LOAN_LIMIT",
                        "PRODUCT_LOAN_LIMIT_FIRST_HOME", "PRODUCT_LOAN_LIMIT_MULTI_CHILD", "LTV", "DTI_LIMIT",
                        "MAX_TERM_MONTHS");

        // 상품별로 쓸 수 있는 키 (명세 5장 표의 칸)
        public static final Map<ProductCode, List<String>> KEYS = Map.of(
                        ProductCode.DIDIMDOL, DIDIMDOL_KEYS,
                        ProductCode.NEWBORN_DIDIMDOL, NEWBORN_KEYS,
                        ProductCode.BOGEUMJARI, BOGEUMJARI_KEYS);

        private CriteriaRules() {
        }

        // 값이 그 키에 맞지 않으면 이유, 맞으면 빈 값
        public static Optional<String> invalid(ProductCode product, String key, BigDecimal value) {
                if (!KEYS.get(product).contains(key)) {
                        return Optional.of(key + " is not a criteria key of " + product);
                }
                if (value == null) {
                        return Optional.of(key + " value is required");
                }
                if (key.startsWith("LTV") || key.equals("DTI_LIMIT")) {
                        return value.signum() > 0 && value.compareTo(BigDecimal.ONE) <= 0 && value.scale() <= 4
                                        ? Optional.empty()
                                        : Optional.of(key
                                                        + " must be a ratio greater than 0 and at most 1 (4 decimal places)");
                }
                boolean integer = value.stripTrailingZeros().scale() <= 0;
                if (key.equals("MAX_TERM_MONTHS")) {
                        return integer && value.compareTo(BigDecimal.valueOf(12)) >= 0
                                        && value.compareTo(BigDecimal.valueOf(600)) <= 0
                                                        ? Optional.empty()
                                                        : Optional.of(key
                                                                        + " must be a whole number of months between 12 and 600");
                }
                return integer && value.compareTo(BigDecimal.ONE) >= 0
                                && value.compareTo(new BigDecimal("99999999999")) <= 0
                                                ? Optional.empty()
                                                : Optional.of(key + " must be a whole amount of at least 1 won");
        }
}
