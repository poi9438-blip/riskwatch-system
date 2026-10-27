package com.riskwatch.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskwatch.common.TraceIds;
import com.riskwatch.domain.ProductCode;
import com.riskwatch.domain.SourceSystem;
import com.riskwatch.domain.Verdict;
import com.riskwatch.repository.CriteriaVersionRepository;
import com.riskwatch.rule.EvaluationInput;
import com.riskwatch.rule.RuleReason;
import com.riskwatch.support.SpecCriteria;
import com.riskwatch.util.EncryptionUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

// 기준 버전 선택과 평가 기록 (DB 포함, 평가 API는 4단계)
@SpringBootTest
@ActiveProfiles("test")
class EvaluationServiceTest {

    @Autowired
    EvaluationService evaluationService;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EncryptionUtil encryptionUtil;
    @Autowired
    ObjectMapper objectMapper;
    // 실제 저장소를 감싸서 "적용 중인 버전 없음"만 일부러 만듦 (TC-EVAL-204)
    @SpyBean
    CriteriaVersionRepository versionRepository;

    @AfterEach
    void clearMdc() {
        MDC.remove(TraceIds.MDC_KEY);
    }

    private static EvaluationInput input(ProductCode product) {
        SpecCriteria.Input input = SpecCriteria.pass(product);
        input.householdAnnualIncome = 54_321_000;
        input.dti = new BigDecimal("0.3123");
        return input.build();
    }

    private Map<String, Object> row(String evaluationId) {
        return jdbc.queryForMap("select e.*, v.version as criteria_version from risk_evaluations e"
                + " join criteria_versions v on v.id = e.criteria_version_id where e.evaluation_id = ?", evaluationId);
    }

    // 테스트가 만든 기준 버전을 지움 (같은 DB를 쓰는 다른 테스트가 첫 버전으로 판정하도록)
    private void deleteVersion(String version) {
        jdbc.update("delete from risk_evaluations where criteria_version_id in"
                + " (select id from criteria_versions where version = ?)", version);
        jdbc.update("delete from criteria_values where version_id in (select id from criteria_versions where version = ?)",
                version);
        jdbc.update("delete from criteria_versions where version = ?", version);
    }

    // 첫 버전 값을 복사해서 새 버전을 만듦 (한 값만 바꿈)
    private void copyVersion(String version, LocalDateTime effectiveFrom, ProductCode product, String key,
            BigDecimal value) {
        jdbc.update("insert into criteria_versions (version, effective_from, created_by, reason, created_at)"
                + " values (?, ?, 'test', 'test', ?)", version, effectiveFrom, effectiveFrom);
        jdbc.update("insert into criteria_values (version_id, product_code, criteria_key, criteria_value)"
                + " select (select id from criteria_versions where version = ?), product_code, criteria_key,"
                + " criteria_value from criteria_values where version_id = (select id from criteria_versions"
                + " where version = ?)", version, SpecCriteria.VERSION);
        jdbc.update("update criteria_values set criteria_value = ? where product_code = ? and criteria_key = ?"
                + " and version_id = (select id from criteria_versions where version = ?)", value, product.name(), key,
                version);
    }

    @Test
    @DisplayName("[TC-EVAL-201] 평가 한 건 → 기록 1건: evaluationId(UUID), 판정·점수, 기준 버전 2026-10-26, 근거 7개 JSON, traceId, 시각(마이크로초)")
    void evaluate_recordsEvaluation() {
        MDC.put(TraceIds.MDC_KEY, "hfm-eval-201");
        EvaluationService.Result result = evaluationService.evaluate(input(ProductCode.DIDIMDOL), SourceSystem.FINFLOW);

        assertThat(result.evaluationId()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(result.criteriaVersion()).isEqualTo(SpecCriteria.VERSION);
        assertThat(result.outcome().verdict()).isEqualTo(Verdict.ELIGIBLE);
        assertThat(result.createdAt()).isEqualTo(result.createdAt().truncatedTo(ChronoUnit.MICROS));

        Map<String, Object> row = row(result.evaluationId());
        assertThat(row).containsEntry("SOURCE_SYSTEM", "FINFLOW").containsEntry("SOURCE_REF", "app-1")
                .containsEntry("APPLICANT_REF", "homefin:1").containsEntry("PRODUCT_CODE", "DIDIMDOL")
                .containsEntry("VERDICT", "ELIGIBLE").containsEntry("RISK_LEVEL", "LOW").containsEntry("RISK_SCORE", 0)
                .containsEntry("CRITERIA_VERSION", SpecCriteria.VERSION).containsEntry("TRACE_ID", "hfm-eval-201");
        assertThat((String) row.get("REASONS")).startsWith("[").contains("INCOME_LIMIT").contains("TERM_LIMIT");
    }

    @Test
    @DisplayName("[TC-EVAL-202] 평가 기록의 연소득·DTI 칸은 암호문 (요청 값이 그대로 없음), 복호화하면 요청 값")
    void evaluate_encryptsIncomeAndDti() {
        String id = evaluationService.evaluate(input(ProductCode.BOGEUMJARI), SourceSystem.HOMEFIN).evaluationId();
        Map<String, Object> row = row(id);
        String income = (String) row.get("HOUSEHOLD_ANNUAL_INCOME");
        String dti = (String) row.get("DTI");
        assertThat(income).doesNotContain("54321000");
        assertThat(dti).doesNotContain("0.3123");
        assertThat(encryptionUtil.decrypt(income)).isEqualTo("54321000");
        assertThat(encryptionUtil.decrypt(dti)).isEqualTo("0.3123");
    }

    @Test
    @DisplayName("[TC-EVAL-203] 기준 버전 선택: 적용 시각이 미래인 새 버전은 쓰지 않음, 이미 지난 새 버전이 있으면 그 버전으로 판정")
    void evaluate_choosesVersionInEffect() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        try {
            // 미래 버전: 보금자리론 DTI 기준을 0.20으로 (적용되면 부적격이 됨)
            copyVersion("test-future", now.plusMinutes(10), ProductCode.BOGEUMJARI, "DTI_LIMIT", new BigDecimal("0.20"));
            EvaluationService.Result beforeEffective = evaluationService.evaluate(input(ProductCode.BOGEUMJARI),
                    SourceSystem.HOMEFIN);
            assertThat(beforeEffective.criteriaVersion()).isEqualTo(SpecCriteria.VERSION);
            assertThat(beforeEffective.outcome().verdict()).isEqualTo(Verdict.ELIGIBLE);

            // 이미 지난 새 버전
            copyVersion("test-past", now.minusMinutes(1), ProductCode.BOGEUMJARI, "DTI_LIMIT", new BigDecimal("0.20"));
            EvaluationService.Result after = evaluationService.evaluate(input(ProductCode.BOGEUMJARI),
                    SourceSystem.HOMEFIN);
            assertThat(after.criteriaVersion()).isEqualTo("test-past");
            assertThat(after.outcome().verdict()).isEqualTo(Verdict.INELIGIBLE);
        } finally {
            deleteVersion("test-future");
            deleteVersion("test-past");
        }
    }

    @Test
    @DisplayName("[TC-EVAL-204] 적용 중인 기준 버전이 없으면 평가 거부, 평가 기록 없음")
    void evaluate_noVersionInEffect() {
        long before = jdbc.queryForObject("select count(*) from risk_evaluations", Long.class);
        doReturn(Optional.empty()).when(versionRepository)
                .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(any());

        assertThatThrownBy(() -> evaluationService.evaluate(input(ProductCode.DIDIMDOL), SourceSystem.HOMEFIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No criteria version in effect");
        assertThat(jdbc.queryForObject("select count(*) from risk_evaluations", Long.class)).isEqualTo(before);
    }

    @Test
    @DisplayName("[TC-EVAL-205] Flyway 첫 기준 버전 2026-10-26의 값이 명세 5장 표와 같음 (상품 3개 × 기준 키, 만든 사람 system, 사유 있음)")
    void firstVersion_matchesSpec() {
        Map<String, Object> version = jdbc.queryForMap(
                "select created_by, reason from criteria_versions where version = ?", SpecCriteria.VERSION);
        assertThat(version.get("CREATED_BY")).isEqualTo("system");
        assertThat((String) version.get("REASON")).isNotBlank();

        for (ProductCode product : ProductCode.values()) {
            Map<String, BigDecimal> stored = new HashMap<>();
            jdbc.query("select criteria_key, criteria_value from criteria_values where product_code = ? and version_id ="
                    + " (select id from criteria_versions where version = ?)",
                    rs -> {
                        stored.put(rs.getString(1), rs.getBigDecimal(2));
                    }, product.name(), SpecCriteria.VERSION);
            Map<String, BigDecimal> expected = SpecCriteria.VALUES.get(product);
            assertThat(stored.keySet()).as(product.name()).isEqualTo(expected.keySet());
            expected.forEach((key, value) -> assertThat(stored.get(key)).as(product + " " + key)
                    .isEqualByComparingTo(value));
        }
    }

    @Test
    @DisplayName("[TC-EVAL-206] 저장한 근거 JSON을 다시 읽으면 평가 때 돌려준 근거와 같음")
    void reasonsRoundTrip() throws Exception {
        SpecCriteria.Input changed = SpecCriteria.pass(ProductCode.BOGEUMJARI);
        changed.firstHome = true;
        changed.dti = new BigDecimal("0.6500");
        EvaluationService.Result result = evaluationService.evaluate(changed.build(), SourceSystem.HOMEFIN);

        String json = (String) row(result.evaluationId()).get("REASONS");
        List<RuleReason> stored = objectMapper.readValue(json, new TypeReference<>() {
        });
        assertThat(stored).isEqualTo(result.outcome().reasons());
    }
}
