package com.riskwatch.rule;

import com.riskwatch.domain.HouseOwnership;
import com.riskwatch.domain.ProductCode;
import com.riskwatch.domain.RuleCode;
import com.riskwatch.domain.RuleResult;
import com.riskwatch.domain.Verdict;
import com.riskwatch.support.SpecCriteria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static com.riskwatch.domain.ProductCode.BOGEUMJARI;
import static com.riskwatch.domain.ProductCode.DIDIMDOL;
import static com.riskwatch.domain.ProductCode.NEWBORN_DIDIMDOL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 심사 규칙 엔진 단위 테스트 (DB 없이, 기준값은 명세 5장 첫 기준 버전)
class RuleEngineTest {

    private final RuleEngine engine = new RuleEngine();

    private EvaluationOutcome evaluate(ProductCode product, Consumer<SpecCriteria.Input> change) {
        SpecCriteria.Input input = SpecCriteria.pass(product);
        change.accept(input);
        return engine.evaluate(input.build(), SpecCriteria.criteria(product));
    }

    private static RuleReason reason(EvaluationOutcome outcome, RuleCode code) {
        return outcome.reasons().stream().filter(r -> r.code() == code).findFirst().orElseThrow();
    }

    private RuleResult result(ProductCode product, RuleCode code, Consumer<SpecCriteria.Input> change) {
        return reason(evaluate(product, change), code).result();
    }

    @Test
    @DisplayName("[TC-RULE-201] 세 상품 모두 기준을 다 충족 → 적격, LOW, 점수 0, 근거 7개 모두 PASS(규칙 순서 고정), 판정하지 않는 기준 목록")
    void allPass() {
        for (ProductCode product : ProductCode.values()) {
            EvaluationOutcome outcome = evaluate(product, i -> {
            });
            assertThat(outcome.verdict()).as(product.name()).isEqualTo(Verdict.ELIGIBLE);
            assertThat(outcome.riskLevel()).isEqualTo("LOW");
            assertThat(outcome.riskScore()).isZero();
            assertThat(outcome.recommendation()).isEqualTo("모든 심사 기준을 충족합니다.");
            assertThat(outcome.reasons()).extracting(RuleReason::code).containsExactly(RuleCode.values());
            assertThat(outcome.reasons()).extracting(RuleReason::result).containsOnly(RuleResult.PASS);
            assertThat(outcome.notEvaluated()).contains("신용점수", "순자산");
        }
        assertThat(evaluate(BOGEUMJARI, i -> {
        }).notEvaluated()).contains("아파트 외 주택 LTV");
        assertThat(evaluate(NEWBORN_DIDIMDOL, i -> {
        }).notEvaluated()).contains("맞벌이 부부 각자 소득 기준");
    }

    @Test
    @DisplayName("[TC-RULE-202] 상품 대상: 신생아 특례 + 2년 내 출산·입양 아님은 FAIL(부적격), 맞으면 PASS, 디딤돌은 출산 여부와 관계없이 PASS")
    void productTarget() {
        EvaluationOutcome notBorn = evaluate(NEWBORN_DIDIMDOL, i -> i.newbornWithin2Years = false);
        assertThat(reason(notBorn, RuleCode.PRODUCT_TARGET).result()).isEqualTo(RuleResult.FAIL);
        assertThat(notBorn.verdict()).isEqualTo(Verdict.INELIGIBLE);
        assertThat(result(NEWBORN_DIDIMDOL, RuleCode.PRODUCT_TARGET, i -> i.newbornWithin2Years = true))
                .isEqualTo(RuleResult.PASS);
        assertThat(result(DIDIMDOL, RuleCode.PRODUCT_TARGET, i -> i.newbornWithin2Years = false))
                .isEqualTo(RuleResult.PASS);
    }

    @Test
    @DisplayName("[TC-RULE-203] 주택 보유: 보유면 디딤돌·신생아 FAIL, 보금자리론 WARN(검토 필요·MEDIUM·점수 7), 무주택·전월세는 PASS")
    void houseOwnership() {
        assertThat(result(DIDIMDOL, RuleCode.HOUSE_OWNERSHIP, i -> i.houseOwnership = HouseOwnership.OWN))
                .isEqualTo(RuleResult.FAIL);
        assertThat(result(NEWBORN_DIDIMDOL, RuleCode.HOUSE_OWNERSHIP, i -> i.houseOwnership = HouseOwnership.OWN))
                .isEqualTo(RuleResult.FAIL);
        EvaluationOutcome owner = evaluate(BOGEUMJARI, i -> i.houseOwnership = HouseOwnership.OWN);
        assertThat(reason(owner, RuleCode.HOUSE_OWNERSHIP).result()).isEqualTo(RuleResult.WARN);
        assertThat(owner.verdict()).isEqualTo(Verdict.REVIEW);
        assertThat(owner.riskLevel()).isEqualTo("MEDIUM");
        assertThat(owner.riskScore()).isEqualTo(7);
        assertThat(owner.recommendation()).isEqualTo("확인이 필요한 기준이 있습니다: 주택 보유.");
        for (ProductCode product : ProductCode.values()) {
            for (HouseOwnership h : new HouseOwnership[] { HouseOwnership.NONE, HouseOwnership.RENT }) {
                assertThat(result(product, RuleCode.HOUSE_OWNERSHIP, i -> i.houseOwnership = h)).isEqualTo(RuleResult.PASS);
            }
        }
    }

    @Test
    @DisplayName("[TC-RULE-204] 소득: 디딤돌 6,000만 PASS / 1원 넘으면 FAIL, 신혼+생애최초는 8,500만(가장 큰 기준), 보금자리론 자녀 1명 9,000만·2명 1억, 신생아 맞벌이 2억, 설명에 적용 조건")
    void incomeLimit() {
        assertThat(result(DIDIMDOL, RuleCode.INCOME_LIMIT, i -> i.householdAnnualIncome = 60_000_000))
                .isEqualTo(RuleResult.PASS);
        assertThat(result(DIDIMDOL, RuleCode.INCOME_LIMIT, i -> i.householdAnnualIncome = 60_000_001))
                .isEqualTo(RuleResult.FAIL);

        RuleReason both = reason(evaluate(DIDIMDOL, i -> {
            i.newlywed = true;
            i.firstHome = true;
            i.householdAnnualIncome = 85_000_000;
        }), RuleCode.INCOME_LIMIT);
        assertThat(both.result()).isEqualTo(RuleResult.PASS);
        assertThat(both.threshold()).isEqualByComparingTo("85000000");
        assertThat(both.detail()).contains("85,000,000원").contains("신혼");

        assertThat(reason(evaluate(BOGEUMJARI, i -> i.childCount = 1), RuleCode.INCOME_LIMIT).threshold())
                .isEqualByComparingTo("90000000");
        assertThat(reason(evaluate(BOGEUMJARI, i -> i.childCount = 2), RuleCode.INCOME_LIMIT).threshold())
                .isEqualByComparingTo("100000000");
        assertThat(result(BOGEUMJARI, RuleCode.INCOME_LIMIT, i -> {
            i.childCount = 1;
            i.householdAnnualIncome = 90_000_001;
        })).isEqualTo(RuleResult.FAIL);

        assertThat(result(NEWBORN_DIDIMDOL, RuleCode.INCOME_LIMIT, i -> i.householdAnnualIncome = 150_000_000))
                .isEqualTo(RuleResult.FAIL);
        RuleReason dual = reason(evaluate(NEWBORN_DIDIMDOL, i -> {
            i.dualIncome = true;
            i.householdAnnualIncome = 200_000_000;
        }), RuleCode.INCOME_LIMIT);
        assertThat(dual.result()).isEqualTo(RuleResult.PASS);
        assertThat(dual.detail()).contains("맞벌이");
    }

    @Test
    @DisplayName("[TC-RULE-205] 주택 가격: 디딤돌 5억 PASS / 1원 넘으면 FAIL, 디딤돌 자녀 2명 6억, 신생아 9억, 보금자리론 6억 1원 FAIL")
    void housePriceLimit() {
        assertThat(result(DIDIMDOL, RuleCode.HOUSE_PRICE_LIMIT, i -> i.housePrice = 500_000_000)).isEqualTo(RuleResult.PASS);
        assertThat(result(DIDIMDOL, RuleCode.HOUSE_PRICE_LIMIT, i -> i.housePrice = 500_000_001)).isEqualTo(RuleResult.FAIL);
        assertThat(result(DIDIMDOL, RuleCode.HOUSE_PRICE_LIMIT, i -> {
            i.childCount = 2;
            i.housePrice = 600_000_000;
        })).isEqualTo(RuleResult.PASS);
        assertThat(result(NEWBORN_DIDIMDOL, RuleCode.HOUSE_PRICE_LIMIT, i -> i.housePrice = 900_000_000))
                .isEqualTo(RuleResult.PASS);
        assertThat(result(NEWBORN_DIDIMDOL, RuleCode.HOUSE_PRICE_LIMIT, i -> i.housePrice = 900_000_001))
                .isEqualTo(RuleResult.FAIL);
        assertThat(result(BOGEUMJARI, RuleCode.HOUSE_PRICE_LIMIT, i -> i.housePrice = 600_000_001))
                .isEqualTo(RuleResult.FAIL);
    }

    @Test
    @DisplayName("[TC-RULE-206] 대출 한도 = min(상품 한도, 주택 가격 × LTV): 주택 2억 → 1.4억 경계, 생애최초+부산 80%, +서울·지역 없음 70%, 보금자리론 생애최초 4.2억, 원 단위 아래 버림")
    void loanLimit() {
        assertThat(result(DIDIMDOL, RuleCode.LOAN_LIMIT, i -> {
            i.housePrice = 200_000_000;
            i.requestedAmount = 140_000_000;
        })).isEqualTo(RuleResult.PASS);
        RuleReason over = reason(evaluate(DIDIMDOL, i -> {
            i.housePrice = 200_000_000;
            i.requestedAmount = 140_000_001;
        }), RuleCode.LOAN_LIMIT);
        assertThat(over.result()).isEqualTo(RuleResult.FAIL);
        assertThat(over.threshold()).isEqualByComparingTo("140000000");
        assertThat(over.detail()).contains("200,000,000원").contains("70%").contains("140,000,000원");

        // 생애최초: 상품 한도 2.4억, 주택 2억 × 80% = 1.6억 (부산) / × 70% = 1.4억 (서울, 지역 없음)
        Consumer<SpecCriteria.Input> firstHome = i -> {
            i.firstHome = true;
            i.housePrice = 200_000_000;
            i.requestedAmount = 160_000_000;
        };
        assertThat(result(DIDIMDOL, RuleCode.LOAN_LIMIT, firstHome.andThen(i -> i.region = "부산")))
                .isEqualTo(RuleResult.PASS);
        assertThat(result(DIDIMDOL, RuleCode.LOAN_LIMIT, firstHome.andThen(i -> i.region = "서울")))
                .isEqualTo(RuleResult.FAIL);
        assertThat(result(DIDIMDOL, RuleCode.LOAN_LIMIT, firstHome.andThen(i -> i.region = null)))
                .isEqualTo(RuleResult.FAIL);

        // 보금자리론 생애최초: 상품 한도 4.2억 (주택 6억 × 70% = 4.2억)
        assertThat(reason(evaluate(BOGEUMJARI, i -> {
            i.firstHome = true;
            i.housePrice = 600_000_000;
            i.requestedAmount = 420_000_000;
        }), RuleCode.LOAN_LIMIT).result()).isEqualTo(RuleResult.PASS);

        // 주택 가격 × LTV 원 단위 아래 버림: 123,456,785 × 0.7 = 86,419,749.5 → 86,419,749 (반올림이면 750)
        RuleReason rounded = reason(evaluate(DIDIMDOL, i -> {
            i.housePrice = 123_456_785;
            i.requestedAmount = 86_419_750;
        }), RuleCode.LOAN_LIMIT);
        assertThat(rounded.threshold()).isEqualByComparingTo("86419749");
        assertThat(rounded.result()).isEqualTo(RuleResult.FAIL);
    }

    @Test
    @DisplayName("[TC-RULE-207] DTI 0.6000 PASS / 0.6001 FAIL")
    void dtiLimit() {
        assertThat(result(DIDIMDOL, RuleCode.DTI_LIMIT, i -> i.dti = new BigDecimal("0.6000"))).isEqualTo(RuleResult.PASS);
        assertThat(result(DIDIMDOL, RuleCode.DTI_LIMIT, i -> i.dti = new BigDecimal("0.6001"))).isEqualTo(RuleResult.FAIL);
    }

    @Test
    @DisplayName("[TC-RULE-208] 기간: 디딤돌 360개월 PASS / 361 FAIL, 보금자리론 600개월 PASS")
    void termLimit() {
        assertThat(result(DIDIMDOL, RuleCode.TERM_LIMIT, i -> i.termMonths = 360)).isEqualTo(RuleResult.PASS);
        assertThat(result(DIDIMDOL, RuleCode.TERM_LIMIT, i -> i.termMonths = 361)).isEqualTo(RuleResult.FAIL);
        assertThat(result(BOGEUMJARI, RuleCode.TERM_LIMIT, i -> i.termMonths = 600)).isEqualTo(RuleResult.PASS);
    }

    @Test
    @DisplayName("[TC-RULE-209] FAIL 2개 + WARN 1개 → 부적격, HIGH, 점수 36, 안내문에 미충족·확인 필요 규칙 이름")
    void verdictScoreAndRecommendation() {
        EvaluationOutcome outcome = evaluate(BOGEUMJARI, i -> {
            i.houseOwnership = HouseOwnership.OWN;
            i.dti = new BigDecimal("0.7000");
            i.termMonths = 601;
        });
        assertThat(outcome.verdict()).isEqualTo(Verdict.INELIGIBLE);
        assertThat(outcome.riskLevel()).isEqualTo("HIGH");
        assertThat(outcome.riskScore()).isEqualTo(36);
        assertThat(outcome.recommendation())
                .isEqualTo("충족하지 않는 기준이 있습니다: DTI, 대출 기간. 확인이 필요한 기준: 주택 보유.");
    }

    @Test
    @DisplayName("[TC-RULE-210] 기준 버전에 그 상품의 필수 기준(보금자리론 DTI_LIMIT 등)이 없으면 평가 거부, 다른 값으로 대신 판정하지 않음")
    void missingCriteriaRejected() {
        for (String key : Criteria.REQUIRED) {
            Map<String, BigDecimal> values = new HashMap<>(SpecCriteria.VALUES.get(BOGEUMJARI));
            values.remove(key);
            assertThatThrownBy(() -> new Criteria("broken", BOGEUMJARI, values))
                    .as(key)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(key);
        }
        // 다른 상품 기준값으로 평가하려 하면 거부
        assertThatThrownBy(() -> engine.evaluate(SpecCriteria.pass(DIDIMDOL).build(), SpecCriteria.criteria(BOGEUMJARI)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[TC-RULE-211] 같은 입력 + 같은 기준으로 두 번 → 판정·점수·근거가 완전히 같음 (결정론적)")
    void deterministic() {
        Arrays.stream(ProductCode.values()).forEach(product -> {
            Consumer<SpecCriteria.Input> change = i -> {
                i.firstHome = true;
                i.childCount = 2;
                i.dti = new BigDecimal("0.5500");
            };
            assertThat(evaluate(product, change)).isEqualTo(evaluate(product, change));
        });
    }
}
