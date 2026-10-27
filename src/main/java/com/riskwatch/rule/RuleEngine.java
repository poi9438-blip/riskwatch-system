package com.riskwatch.rule;

import com.riskwatch.domain.HouseOwnership;
import com.riskwatch.domain.ProductCode;
import com.riskwatch.domain.RuleCode;
import com.riskwatch.domain.RuleResult;
import com.riskwatch.domain.Verdict;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// 기준 버전과 입력을 함께 기록
// 심사 규칙 7개로 판정과 근거를 만듦 (업무 명세 4장, 기준값은 Criteria)
@Component
public class RuleEngine {

    private static final Set<String> CAPITAL_AREA = Set.of("서울", "인천", "경기");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public EvaluationOutcome evaluate(EvaluationInput in, Criteria c) {
        if (in.productCode() != c.productCode()) {
            throw new IllegalArgumentException("Criteria product " + c.productCode() + " does not match input "
                    + in.productCode());
        }
        List<RuleReason> reasons = List.of(productTarget(in), houseOwnership(in), income(in, c), housePrice(in, c),
                loanLimit(in, c), dti(in, c), term(in, c));

        long fails = reasons.stream().filter(r -> r.result() == RuleResult.FAIL).count();
        long warns = reasons.stream().filter(r -> r.result() == RuleResult.WARN).count();
        Verdict verdict = fails > 0 ? Verdict.INELIGIBLE : warns > 0 ? Verdict.REVIEW : Verdict.ELIGIBLE;
        // 점수 = round(100 × (FAIL 수 + 0.5 × WARN 수) ÷ 규칙 수), 판정을 바꾸지 않는 보조 값
        int score = BigDecimal.valueOf(fails).add(BigDecimal.valueOf(warns).multiply(new BigDecimal("0.5")))
                .multiply(HUNDRED).divide(BigDecimal.valueOf(reasons.size()), 0, RoundingMode.HALF_UP).intValue();

        return new EvaluationOutcome(verdict, verdict.riskLevel(), score, recommendation(verdict, reasons), reasons,
                notEvaluated(in.productCode()));
    }

    // 상품 대상: 신생아 특례는 2년 내 출산·입양 가구만
    private RuleReason productTarget(EvaluationInput in) {
        if (in.productCode() == ProductCode.NEWBORN_DIDIMDOL) {
            return in.newbornWithin2Years()
                    ? reason(RuleCode.PRODUCT_TARGET, null, null, RuleResult.PASS, "2년 안에 출산·입양한 가구")
                    : reason(RuleCode.PRODUCT_TARGET, null, null, RuleResult.FAIL, "2년 안에 출산·입양한 가구만 이용할 수 있음");
        }
        return reason(RuleCode.PRODUCT_TARGET, null, null, RuleResult.PASS, "대상 제한 없음");
    }

    // 주택 보유: 디딤돌·신생아는 무주택만, 보금자리론은 1주택이면 처분 조건 확인 필요
    private RuleReason houseOwnership(EvaluationInput in) {
        if (in.houseOwnership() != HouseOwnership.OWN) {
            return reason(RuleCode.HOUSE_OWNERSHIP, null, null, RuleResult.PASS, "무주택");
        }
        return in.productCode() == ProductCode.BOGEUMJARI
                ? reason(RuleCode.HOUSE_OWNERSHIP, null, null, RuleResult.WARN,
                        "주택 보유: 1주택이면 기존 주택을 처분하는 조건으로 가능, 처분 조건 확인 필요")
                : reason(RuleCode.HOUSE_OWNERSHIP, null, null, RuleResult.FAIL, "세대원 전원 무주택이어야 함");
    }

    // 소득: 해당하는 가구 조건 기준 중 가장 큰 값 이하면 PASS
    private RuleReason income(EvaluationInput in, Criteria c) {
        Limit limit = new Limit(c.required("INCOME_LIMIT"), "기본");
        if (in.firstHome()) {
            limit = limit.max(c.optional("INCOME_LIMIT_FIRST_HOME"), "생애최초");
        }
        if (in.newlywed()) {
            limit = limit.max(c.optional("INCOME_LIMIT_NEWLYWED"), "신혼");
        }
        if (in.childCount() >= 1) {
            limit = limit.max(c.optional("INCOME_LIMIT_ONE_CHILD"), "자녀 1명");
        }
        if (in.childCount() >= 2) {
            limit = limit.max(c.optional("INCOME_LIMIT_MULTI_CHILD"), "자녀 2명 이상");
        }
        if (in.dualIncome()) {
            limit = limit.max(c.optional("INCOME_LIMIT_DUAL_INCOME"), "맞벌이");
        }
        return atMost(RuleCode.INCOME_LIMIT, limit.value(), BigDecimal.valueOf(in.householdAnnualIncome()),
                "부부합산 연소득 기준 " + won(limit.value()) + " (" + limit.basis() + " 기준)");
    }

    // 주택 가격: 디딤돌은 신혼·자녀 2명 이상이면 높은 기준
    private RuleReason housePrice(EvaluationInput in, Criteria c) {
        Limit limit = new Limit(c.required("HOUSE_PRICE_LIMIT"), "기본");
        if (in.newlywed() || in.childCount() >= 2) {
            limit = limit.max(c.optional("HOUSE_PRICE_LIMIT_NEWLYWED_OR_MULTI_CHILD"), "신혼·자녀 2명 이상");
        }
        return atMost(RuleCode.HOUSE_PRICE_LIMIT, limit.value(), BigDecimal.valueOf(in.housePrice()),
                "주택 가격 기준 " + won(limit.value()) + " (" + limit.basis() + " 기준)");
    }

    // 대출 금액: min(상품 한도, 주택 가격 × LTV) 이하면 PASS
    private RuleReason loanLimit(EvaluationInput in, Criteria c) {
        Limit product = new Limit(c.required("PRODUCT_LOAN_LIMIT"), "기본");
        if (in.firstHome()) {
            product = product.max(c.optional("PRODUCT_LOAN_LIMIT_FIRST_HOME"), "생애최초");
        }
        if (in.newlywed() || in.childCount() >= 2) {
            product = product.max(c.optional("PRODUCT_LOAN_LIMIT_NEWLYWED_OR_MULTI_CHILD"), "신혼·자녀 2명 이상");
        }
        if (in.childCount() >= 2) {
            product = product.max(c.optional("PRODUCT_LOAN_LIMIT_MULTI_CHILD"), "자녀 2명 이상");
        }
        BigDecimal ltv = c.required("LTV");
        boolean outsideCapital = in.region() != null && !CAPITAL_AREA.contains(in.region().trim());
        if (in.firstHome() && outsideCapital) {
            ltv = c.optional("LTV_FIRST_HOME_NON_CAPITAL").map(v -> v.max(c.required("LTV"))).orElse(ltv);
        }
        BigDecimal byPrice = BigDecimal.valueOf(in.housePrice()).multiply(ltv).setScale(0, RoundingMode.DOWN);
        BigDecimal limit = product.value().min(byPrice);
        return atMost(RuleCode.LOAN_LIMIT, limit, BigDecimal.valueOf(in.requestedAmount()),
                "대출 한도 " + won(limit) + " = min(상품 한도 " + won(product.value()) + " (" + product.basis()
                        + " 기준), 주택 가격 × LTV " + percent(ltv) + " = " + won(byPrice) + ")");
    }

    private RuleReason dti(EvaluationInput in, Criteria c) {
        BigDecimal limit = c.required("DTI_LIMIT");
        return atMost(RuleCode.DTI_LIMIT, limit, in.dti(), "DTI 기준 " + percent(limit) + " 이하");
    }

    private RuleReason term(EvaluationInput in, Criteria c) {
        BigDecimal limit = c.required("MAX_TERM_MONTHS");
        return atMost(RuleCode.TERM_LIMIT, limit, BigDecimal.valueOf(in.termMonths()),
                "최대 " + limit.stripTrailingZeros().toPlainString() + "개월");
    }

    // 실제값 ≤ 기준값이면 PASS, 넘으면 FAIL
    private static RuleReason atMost(RuleCode code, BigDecimal threshold, BigDecimal actual, String detail) {
        RuleResult result = actual.compareTo(threshold) <= 0 ? RuleResult.PASS : RuleResult.FAIL;
        return reason(code, threshold, actual, result, detail);
    }

    private static RuleReason reason(RuleCode code, BigDecimal threshold, BigDecimal actual, RuleResult result,
            String detail) {
        return new RuleReason(code, code.label(), threshold == null ? null : threshold.stripTrailingZeros(),
                actual == null ? null : actual.stripTrailingZeros(), result, detail);
    }

    // 판정 안내문 (미충족·확인 필요 규칙 이름)
    private static String recommendation(Verdict verdict, List<RuleReason> reasons) {
        String failed = names(reasons, RuleResult.FAIL);
        String warned = names(reasons, RuleResult.WARN);
        return switch (verdict) {
            case ELIGIBLE -> "모든 심사 기준을 충족합니다.";
            case REVIEW -> "확인이 필요한 기준이 있습니다: " + warned + ".";
            case INELIGIBLE -> "충족하지 않는 기준이 있습니다: " + failed + "."
                    + (warned.isEmpty() ? "" : " 확인이 필요한 기준: " + warned + ".");
        };
    }

    private static String names(List<RuleReason> reasons, RuleResult result) {
        return reasons.stream().filter(r -> r.result() == result).map(RuleReason::name)
                .collect(Collectors.joining(", "));
    }

    // 입력이 없어 판정하지 않는 기준 (업무 명세 10장)
    private static List<String> notEvaluated(ProductCode product) {
        List<String> list = new ArrayList<>(List.of("신용점수", "순자산", "전용면적", "규제지역 LTV·DTI 차감"));
        if (product == ProductCode.BOGEUMJARI) {
            list.add("아파트 외 주택 LTV");
        }
        if (product == ProductCode.NEWBORN_DIDIMDOL) {
            list.add("맞벌이 부부 각자 소득 기준");
        }
        return List.copyOf(list);
    }

    private static String won(BigDecimal amount) {
        return new DecimalFormat("#,###").format(amount) + "원";
    }

    private static String percent(BigDecimal ratio) {
        return ratio.multiply(HUNDRED).stripTrailingZeros().toPlainString() + "%";
    }

    // 기준값과 그 기준을 쓴 조건 (더 큰 기준이 있으면 바꿈)
    private record Limit(BigDecimal value, String basis) {
        Limit max(Optional<BigDecimal> candidate, String candidateBasis) {
            return candidate.filter(v -> v.compareTo(value) > 0).map(v -> new Limit(v, candidateBasis)).orElse(this);
        }
    }
}
