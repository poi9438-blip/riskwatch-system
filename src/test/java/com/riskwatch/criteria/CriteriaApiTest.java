package com.riskwatch.criteria;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskwatch.domain.ProductCode;
import com.riskwatch.domain.SourceSystem;
import com.riskwatch.domain.Verdict;
import com.riskwatch.service.EvaluationService;
import com.riskwatch.support.ApiTestSupport;
import com.riskwatch.support.SpecCriteria;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.riskwatch.support.ApiTestSupport.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 운영자 기준표 API·평가 기록 목록 통합 테스트 (보안 필터·DB 포함)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CriteriaApiTest {

    private static final String VERSIONS = "/api/admin/criteria/versions";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EvaluationService evaluationService;

    private ApiTestSupport api;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestSupport(mockMvc, objectMapper);
        token = api.operatorToken();
    }

    @AfterEach
    void cleanUp() {
        ApiTestSupport.deleteCreatedVersions(jdbc);
    }

    private static Map<String, Object> change(String product, String key, Object value) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("productCode", product);
        c.put("key", key);
        c.put("value", value);
        return c;
    }

    private static Map<String, Object> request(String version, Object effectiveFrom, List<Map<String, Object>> changes) {
        Map<String, Object> body = new HashMap<>();
        body.put("version", version);
        body.put("effectiveFrom", effectiveFrom);
        body.put("reason", "테스트 변경");
        body.put("changes", changes);
        return body;
    }

    private long versionCount() {
        return jdbc.queryForObject("select count(*) from criteria_versions", Long.class);
    }

    @Test
    @DisplayName("[TC-CRIT-101] 지금 적용 중인 기준 조회 → 200, 버전 2026-10-26, 상품 3개의 값 전체 (명세 5장 표)")
    void current() throws Exception {
        JsonNode data = api.read(api.adminGet(token, "/api/admin/criteria").andExpect(status().isOk()).andReturn())
                .get("data");
        assertThat(data.at("/summary/version").asText()).isEqualTo(SpecCriteria.VERSION);
        assertThat(data.at("/summary/status").asText()).isEqualTo("ACTIVE");
        SpecCriteria.VALUES.forEach((product, values) -> {
            JsonNode stored = data.at("/values/" + product.name());
            assertThat(stored.size()).as(product.name()).isEqualTo(values.size());
            values.forEach((key, value) -> assertThat(stored.get(key).decimalValue()).as(product + " " + key)
                    .isEqualByComparingTo(value));
        });
    }

    @Test
    @DisplayName("[TC-CRIT-102] 새 버전(보금자리론 DTI 0.60 → 0.55, 즉시) → 201, 바뀐 값 1개, 만든 사람 operator:{id}(본문 무시), 나머지 복사, 바로 다음 평가부터 새 버전")
    void createImmediateVersion() throws Exception {
        Map<String, Object> body = request("2026-11-dti", null, List.of(change("BOGEUMJARI", "DTI_LIMIT", "0.55")));
        body.put("createdBy", "someone-else");
        JsonNode data = api.read(api.adminPost(token, VERSIONS, body).andExpect(status().isCreated()).andReturn())
                .get("data");

        assertThat(data.at("/summary/status").asText()).isEqualTo("ACTIVE");
        assertThat(data.at("/summary/createdBy").asText()).matches("operator:\\d+");
        assertThat(data.get("previousVersion").asText()).isEqualTo(SpecCriteria.VERSION);
        assertThat(data.get("changes")).hasSize(1);
        assertThat(data.at("/changes/0/key").asText()).isEqualTo("DTI_LIMIT");
        assertThat(data.at("/changes/0/before").decimalValue()).isEqualByComparingTo("0.6");
        assertThat(data.at("/changes/0/after").decimalValue()).isEqualByComparingTo("0.55");
        assertThat(data.at("/values/DIDIMDOL/DTI_LIMIT").decimalValue()).isEqualByComparingTo("0.6");
        assertThat(jdbc.queryForObject("select count(*) from criteria_values where version_id ="
                + " (select id from criteria_versions where version = '2026-11-dti')", Long.class)).isEqualTo(32);

        // 바로 다음 평가는 새 버전으로 (DTI 0.58 → 이전 기준이면 적격, 새 기준이면 부적격)
        SpecCriteria.Input input = SpecCriteria.pass(ProductCode.BOGEUMJARI);
        input.dti = new BigDecimal("0.5800");
        EvaluationService.Result result = evaluationService.evaluate(input.build(), SourceSystem.FINFLOW);
        assertThat(result.criteriaVersion()).isEqualTo("2026-11-dti");
        assertThat(result.outcome().verdict()).isEqualTo(Verdict.INELIGIBLE);
    }

    @Test
    @DisplayName("[TC-CRIT-103] 적용 시각 1시간 뒤 새 버전 → 201, 지금 적용 중인 버전은 그대로, 목록에 예약(SCHEDULED)")
    void createScheduledVersion() throws Exception {
        String later = OffsetDateTime.now(ZoneOffset.ofHours(9)).plusHours(1).toString();
        api.adminPost(token, VERSIONS, request("2026-12-scheduled", later,
                List.of(change("DIDIMDOL", "INCOME_LIMIT", 65_000_000))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.summary.status").value("SCHEDULED"));

        api.adminGet(token, "/api/admin/criteria")
                .andExpect(jsonPath("$.data.summary.version").value(SpecCriteria.VERSION));
        api.adminGet(token, VERSIONS)
                .andExpect(jsonPath("$.data[0].version").value("2026-12-scheduled"))
                .andExpect(jsonPath("$.data[0].status").value("SCHEDULED"));
    }

    @Test
    @DisplayName("[TC-CRIT-104] 예약된 버전이 있는데 또 새 버전 / 이미 있는 버전 이름 → 둘 다 409, 버전 추가 없음")
    void conflicts() throws Exception {
        api.adminPost(token, VERSIONS, request("dup-name", null, List.of(change("DIDIMDOL", "LTV", "0.65"))))
                .andExpect(status().isCreated());
        long before = versionCount();
        api.adminPost(token, VERSIONS, request("dup-name", null, List.of(change("DIDIMDOL", "LTV", "0.6"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Criteria version already exists"));

        String later = OffsetDateTime.now(ZoneOffset.UTC).plusHours(2).toString();
        api.adminPost(token, VERSIONS, request("scheduled-1", later, List.of(change("DIDIMDOL", "LTV", "0.6"))))
                .andExpect(status().isCreated());
        api.adminPost(token, VERSIONS, request("scheduled-2", later, List.of(change("DIDIMDOL", "LTV", "0.55"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A scheduled criteria version already exists"));
        assertThat(versionCount()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("[TC-CRIT-105] 잘못된 변경(범위 밖·없는 키·없는 상품·같은 키 두 번·지금 값과 같음·사유 없음·변경 없음·과거 적용 시각·버전 이름 형식) → 모두 400, 버전 추가 없음")
    void invalidChanges() throws Exception {
        long before = versionCount();
        List<Map<String, Object>> invalid = new ArrayList<>();
        for (Object[] c : new Object[][] {
                { "BOGEUMJARI", "PRODUCT_LOAN_LIMIT", 0 }, { "BOGEUMJARI", "PRODUCT_LOAN_LIMIT", "1.5" },
                { "DIDIMDOL", "LTV", 0 }, { "DIDIMDOL", "LTV", "1.01" }, { "DIDIMDOL", "DTI_LIMIT", "1.0001" },
                { "DIDIMDOL", "MAX_TERM_MONTHS", 11 }, { "BOGEUMJARI", "MAX_TERM_MONTHS", 601 },
                { "DIDIMDOL", "INCOME_LIMIT_ONE_CHILD", 90_000_000 }, { "GUARANTEE", "LTV", "0.7" },
                { "BOGEUMJARI", "DTI_LIMIT", "0.60" } }) {
            invalid.add(request("bad-" + invalid.size(), null, List.of(change((String) c[0], (String) c[1], c[2]))));
        }
        invalid.add(request("bad-dup", null, List.of(change("DIDIMDOL", "LTV", "0.6"), change("DIDIMDOL", "LTV", "0.65"))));
        Map<String, Object> noReason = request("bad-reason", null, List.of(change("DIDIMDOL", "LTV", "0.6")));
        noReason.put("reason", " ");
        invalid.add(noReason);
        invalid.add(request("bad-empty", null, List.of()));
        invalid.add(request("bad-past", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1).toString(),
                List.of(change("DIDIMDOL", "LTV", "0.6"))));
        invalid.add(request("bad name!", null, List.of(change("DIDIMDOL", "LTV", "0.6"))));

        for (Map<String, Object> body : invalid) {
            api.adminPost(token, VERSIONS, body).andExpect(status().isBadRequest());
        }
        api.adminPost(token, VERSIONS, request("bad-key", null, List.of(change("DIDIMDOL", "INCOME_LIMIT_ONE_CHILD", 1))))
                .andExpect(jsonPath("$.message", containsString("is not a criteria key of DIDIMDOL")));
        assertThat(versionCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("[TC-CRIT-106] 값 범위 양 끝(LTV 1.0, DTI 0.0001, 기간 12·600, 금액 1) → 201")
    void boundaryValuesAccepted() throws Exception {
        Object[][] cases = { { "DIDIMDOL", "LTV", "1.0" }, { "DIDIMDOL", "DTI_LIMIT", "0.0001" },
                { "DIDIMDOL", "MAX_TERM_MONTHS", 12 }, { "BOGEUMJARI", "MAX_TERM_MONTHS", 599 },
                { "BOGEUMJARI", "MAX_TERM_MONTHS", 600 }, { "NEWBORN_DIDIMDOL", "PRODUCT_LOAN_LIMIT", 1 } };
        for (int i = 0; i < cases.length; i++) {
            Object[] c = cases[i];
            api.adminPost(token, VERSIONS, request("edge-" + i, null, List.of(change((String) c[0], (String) c[1], c[2]))))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    @DisplayName("[TC-CRIT-107] 버전 목록은 최신순(상태: 적용 중·예약·지난), 상세는 값 전체 + 이전 버전 대비 바뀐 값, 없는 버전 404")
    void listAndDetail() throws Exception {
        api.adminPost(token, VERSIONS, request("v-now", null, List.of(change("BOGEUMJARI", "INCOME_LIMIT", 72_000_000))))
                .andExpect(status().isCreated());
        api.adminPost(token, VERSIONS, request("v-later", OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).toString(),
                List.of(change("BOGEUMJARI", "INCOME_LIMIT", 75_000_000)))).andExpect(status().isCreated());

        JsonNode list = api.read(api.adminGet(token, VERSIONS).andExpect(status().isOk()).andReturn()).get("data");
        assertThat(list).extracting(n -> n.get("version").asText()).containsExactly("v-later", "v-now", SpecCriteria.VERSION);
        assertThat(list).extracting(n -> n.get("status").asText()).containsExactly("SCHEDULED", "ACTIVE", "PAST");
        assertThat(list.get(0).get("reason").asText()).isEqualTo("테스트 변경");

        JsonNode detail = api.read(api.adminGet(token, VERSIONS + "/v-later").andExpect(status().isOk()).andReturn())
                .get("data");
        assertThat(detail.get("previousVersion").asText()).isEqualTo("v-now");
        assertThat(detail.get("changes")).hasSize(1);
        assertThat(detail.at("/changes/0/before").decimalValue()).isEqualByComparingTo("72000000");
        assertThat(detail.at("/changes/0/after").decimalValue()).isEqualByComparingTo("75000000");
        assertThat(api.read(api.adminGet(token, VERSIONS + "/" + SpecCriteria.VERSION).andReturn())
                .at("/data/changes")).isEmpty();

        api.adminGet(token, VERSIONS + "/no-such-version")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Criteria version not found"));
    }

    @Test
    @DisplayName("[TC-CRIT-108] 기준 버전을 바꾸거나 지우는 API 없음 (PUT·PATCH·DELETE 405), 버전·값 그대로")
    void versionsAreImmutable() throws Exception {
        String path = VERSIONS + "/" + SpecCriteria.VERSION;
        for (var request : List.of(put(path), patch(path), delete(path))) {
            mockMvc.perform(request.header("Authorization", bearer(token))
                    .contentType("application/json").content("{}"))
                    .andExpect(status().isMethodNotAllowed());
        }
        assertThat(jdbc.queryForObject("select count(*) from criteria_values where version_id ="
                + " (select id from criteria_versions where version = ?)", Long.class, SpecCriteria.VERSION))
                .isEqualTo(32);
    }

    @Test
    @DisplayName("[TC-CRIT-109] 평가 기록 목록: 상품·판정 필터, 최신순, 연소득·DTI 없음, size 101은 400")
    void evaluationList() throws Exception {
        SpecCriteria.Input high = SpecCriteria.pass(ProductCode.NEWBORN_DIDIMDOL);
        high.dti = new BigDecimal("0.9000");
        String older = evaluationService.evaluate(SpecCriteria.pass(ProductCode.NEWBORN_DIDIMDOL).build(),
                SourceSystem.HOMEFIN).evaluationId();
        String newer = evaluationService.evaluate(high.build(), SourceSystem.FINFLOW).evaluationId();

        JsonNode all = api.read(api.adminGet(token, "/api/admin/evaluations?productCode=NEWBORN_DIDIMDOL&size=100")
                .andExpect(status().isOk()).andReturn()).get("data");
        List<String> ids = new ArrayList<>();
        all.forEach(n -> {
            ids.add(n.get("evaluationId").asText());
            assertThat(n.get("productCode").asText()).isEqualTo("NEWBORN_DIDIMDOL");
            assertThat(n.has("householdAnnualIncome")).isFalse();
            assertThat(n.has("dti")).isFalse();
        });
        assertThat(ids.indexOf(newer)).isLessThan(ids.indexOf(older));

        JsonNode ineligible = api.read(api.adminGet(token,
                "/api/admin/evaluations?productCode=NEWBORN_DIDIMDOL&verdict=INELIGIBLE&size=100").andReturn()).get("data");
        assertThat(ineligible).isNotEmpty().allSatisfy(n -> assertThat(n.get("verdict").asText()).isEqualTo("INELIGIBLE"));
        assertThat(ineligible.get(0).get("criteriaVersion").asText()).isEqualTo(SpecCriteria.VERSION);

        api.adminGet(token, "/api/admin/evaluations?size=101")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("size must be between 1 and 100")));
    }
}
