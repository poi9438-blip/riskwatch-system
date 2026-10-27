package com.riskwatch.support;

import com.riskwatch.domain.HouseOwnership;
import com.riskwatch.domain.ProductCode;
import com.riskwatch.rule.Criteria;
import com.riskwatch.rule.EvaluationInput;

import java.math.BigDecimal;
import java.util.Map;

// 업무 명세 5장 표 그대로 (첫 기준 버전 2026-10-26). 규칙 엔진 테스트와 Flyway 값 확인(TC-EVAL-205)이 함께 씀
public final class SpecCriteria {

    public static final String VERSION = "2026-10-26";

    public static final Map<ProductCode, Map<String, BigDecimal>> VALUES = Map.of(
            ProductCode.DIDIMDOL, Map.ofEntries(
                    Map.entry("INCOME_LIMIT", won(60_000_000)),
                    Map.entry("INCOME_LIMIT_FIRST_HOME", won(70_000_000)),
                    Map.entry("INCOME_LIMIT_NEWLYWED", won(85_000_000)),
                    Map.entry("INCOME_LIMIT_MULTI_CHILD", won(70_000_000)),
                    Map.entry("HOUSE_PRICE_LIMIT", won(500_000_000)),
                    Map.entry("HOUSE_PRICE_LIMIT_NEWLYWED_OR_MULTI_CHILD", won(600_000_000)),
                    Map.entry("PRODUCT_LOAN_LIMIT", won(200_000_000)),
                    Map.entry("PRODUCT_LOAN_LIMIT_FIRST_HOME", won(240_000_000)),
                    Map.entry("PRODUCT_LOAN_LIMIT_NEWLYWED_OR_MULTI_CHILD", won(320_000_000)),
                    Map.entry("LTV", new BigDecimal("0.70")),
                    Map.entry("LTV_FIRST_HOME_NON_CAPITAL", new BigDecimal("0.80")),
                    Map.entry("DTI_LIMIT", new BigDecimal("0.60")),
                    Map.entry("MAX_TERM_MONTHS", BigDecimal.valueOf(360))),
            ProductCode.NEWBORN_DIDIMDOL, Map.ofEntries(
                    Map.entry("INCOME_LIMIT", won(130_000_000)),
                    Map.entry("INCOME_LIMIT_DUAL_INCOME", won(200_000_000)),
                    Map.entry("HOUSE_PRICE_LIMIT", won(900_000_000)),
                    Map.entry("PRODUCT_LOAN_LIMIT", won(400_000_000)),
                    Map.entry("LTV", new BigDecimal("0.70")),
                    Map.entry("LTV_FIRST_HOME_NON_CAPITAL", new BigDecimal("0.80")),
                    Map.entry("DTI_LIMIT", new BigDecimal("0.60")),
                    Map.entry("MAX_TERM_MONTHS", BigDecimal.valueOf(360))),
            ProductCode.BOGEUMJARI, Map.ofEntries(
                    Map.entry("INCOME_LIMIT", won(70_000_000)),
                    Map.entry("INCOME_LIMIT_NEWLYWED", won(85_000_000)),
                    Map.entry("INCOME_LIMIT_ONE_CHILD", won(90_000_000)),
                    Map.entry("INCOME_LIMIT_MULTI_CHILD", won(100_000_000)),
                    Map.entry("HOUSE_PRICE_LIMIT", won(600_000_000)),
                    Map.entry("PRODUCT_LOAN_LIMIT", won(360_000_000)),
                    Map.entry("PRODUCT_LOAN_LIMIT_FIRST_HOME", won(420_000_000)),
                    Map.entry("PRODUCT_LOAN_LIMIT_MULTI_CHILD", won(400_000_000)),
                    Map.entry("LTV", new BigDecimal("0.70")),
                    Map.entry("DTI_LIMIT", new BigDecimal("0.60")),
                    Map.entry("MAX_TERM_MONTHS", BigDecimal.valueOf(600))));

    private SpecCriteria() {
    }

    private static BigDecimal won(long amount) {
        return BigDecimal.valueOf(amount);
    }

    public static Criteria criteria(ProductCode product) {
        return new Criteria(VERSION, product, VALUES.get(product));
    }

    // 모든 기준을 충족하는 입력 (상품별로 필요한 값만 바꿔 쓰기)
    public static Input pass(ProductCode product) {
        return new Input(product);
    }

    // 입력을 바꿔 가며 만드는 도우미
    public static final class Input {
        public ProductCode productCode;
        public String applicantRef = "homefin:1";
        public String sourceRef = "app-1";
        public long requestedAmount = 100_000_000;
        public int termMonths = 360;
        public long housePrice = 300_000_000;
        public long householdAnnualIncome = 50_000_000;
        public BigDecimal dti = new BigDecimal("0.3000");
        public HouseOwnership houseOwnership = HouseOwnership.NONE;
        public boolean firstHome;
        public boolean newlywed;
        public boolean newbornWithin2Years;
        public boolean dualIncome;
        public int childCount;
        public String region = "부산";

        Input(ProductCode productCode) {
            this.productCode = productCode;
            this.newbornWithin2Years = productCode == ProductCode.NEWBORN_DIDIMDOL;
        }

        public EvaluationInput build() {
            return new EvaluationInput(productCode, applicantRef, sourceRef, requestedAmount, termMonths, housePrice,
                    householdAnnualIncome, dti, houseOwnership, firstHome, newlywed, newbornWithin2Years, dualIncome,
                    childCount, region);
        }
    }
}
