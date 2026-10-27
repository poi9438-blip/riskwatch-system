package com.riskwatch.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskwatch.domain.ProductCode;
import com.riskwatch.support.ApiTestSupport;
import com.riskwatch.support.SpecCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.riskwatch.support.ApiTestSupport.bearer;
import static com.riskwatch.support.ApiTestSupport.evaluationBody;
import static com.riskwatch.support.ApiTestSupport.evaluationCount;
import static com.riskwatch.support.ApiTestSupport.finflowToken;
import static com.riskwatch.support.ApiTestSupport.homefinToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 평가 API 통합 테스트 (서비스 토큰, 입력 검증, 조회 범위). 판정 규칙 자체는 RuleEngineTest
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EvaluationApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    JdbcTemplate jdbc;

    private ApiTestSupport api;

    @BeforeEach
    void setUp() {
        api = new ApiTestSupport(mockMvc, objectMapper);
    }

    private static Map<String, Object> body() {
        return evaluationBody(ProductCode.DIDIMDOL);
    }

    // 평가하고 응답의 data
    private JsonNode evaluateOk(String token, Object body) throws Exception {
        return api.read(api.evaluate(token, body).andExpect(status().isCreated()).andReturn()).get("data");
    }

    private static Object number(String value) {
        return value.contains(".") ? new java.math.BigDecimal(value) : Long.valueOf(value);
    }

    @Test
    @DisplayName("[TC-EVAL-101] 평가 → 201 {evaluationId, verdict, riskLevel, riskScore, recommendation, criteriaVersion, reasons 7개, notEvaluated, createdAt …Z}, 기록 1건(traceId 같음)")
    void evaluate_created() throws Exception {
        long before = evaluationCount(jdbc);
        MvcResult result = mockMvc.perform(post("/api/evaluations").header("Authorization", bearer(homefinToken()))
                .header("X-Trace-Id", "hfm-eval-101").contentType(MediaType.APPLICATION_JSON).content(api.json(body())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.traceId").value("hfm-eval-101"))
                .andReturn();
        JsonNode data = api.read(result).get("data");

        String evaluationId = data.get("evaluationId").asText();
        assertThat(UUID.fromString(evaluationId).toString()).isEqualTo(evaluationId);
        assertThat(data.get("verdict").asText()).isEqualTo("ELIGIBLE");
        assertThat(data.get("riskLevel").asText()).isEqualTo("LOW");
        assertThat(data.get("riskScore").asInt()).isZero();
        assertThat(data.get("recommendation").asText()).isNotBlank();
        assertThat(data.get("criteriaVersion").asText()).isEqualTo(SpecCriteria.VERSION);
        assertThat(data.get("reasons")).hasSize(7);
        assertThat(data.get("reasons").get(0).has("threshold")).isTrue();
        assertThat(data.get("notEvaluated").toString()).contains("신용점수");
        assertThat(data.get("createdAt").asText()).endsWith("Z");
        assertThat(data.get("sourceRef").asText()).isEqualTo("app-1");

        assertThat(evaluationCount(jdbc)).isEqualTo(before + 1);
        Map<String, Object> row = jdbc.queryForMap(
                "select trace_id, source_system, verdict from risk_evaluations where evaluation_id = ?", evaluationId);
        assertThat(row).containsEntry("TRACE_ID", "hfm-eval-101").containsEntry("SOURCE_SYSTEM", "HOMEFIN")
                .containsEntry("VERDICT", "ELIGIBLE");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "productCode", "applicantRef", "requestedAmount", "termMonths", "housePrice",
            "householdAnnualIncome", "dti", "houseOwnership", "firstHome", "newlywed", "newbornWithin2Years",
            "dualIncome", "childCount" })
    @DisplayName("[TC-EVAL-102] 필수 입력을 하나씩 빼면 400 \"필드: 메시지\", 평가 기록 없음")
    void missingRequiredField_badRequest(String field) throws Exception {
        Map<String, Object> body = body();
        body.remove(field);
        long before = evaluationCount(jdbc);

        MvcResult result = api.evaluate(homefinToken(), body).andExpect(status().isBadRequest()).andReturn();
        assertThat(api.read(result).get("message").asText()).startsWith(field + ": ").endsWith("is required");
        assertThat(evaluationCount(jdbc)).isEqualTo(before);
    }

    @ParameterizedTest(name = "{0}={1} → {2}")
    @CsvSource({
            "requestedAmount, 1, 201", "requestedAmount, 1000000000, 201",
            "requestedAmount, 0, 400", "requestedAmount, 1000000001, 400",
            "termMonths, 12, 201", "termMonths, 600, 201", "termMonths, 11, 400", "termMonths, 601, 400",
            "dti, 9.9999, 201", "dti, 10, 400", "dti, 0.12345, 400",
            "childCount, 20, 201", "childCount, 21, 400",
            "housePrice, 1, 201", "housePrice, 0, 400",
            "householdAnnualIncome, 0, 201", "householdAnnualIncome, -1, 400",
            "applicantRef, homefin:12, 201", "applicantRef, user:12, 400",
            "sourceRef, 64, 201", "sourceRef, 65, 400",
            "region, 20, 201", "region, 21, 400" })
    @DisplayName("[TC-EVAL-103] 입력 범위의 양쪽: 범위 안은 201, 밖은 400")
    void inputBoundaries(String field, String value, int expected) throws Exception {
        Map<String, Object> body = body();
        // sourceRef·region은 글자 수로 지정
        body.put(field, switch (field) {
            case "sourceRef", "region" -> "가".repeat(Integer.parseInt(value));
            case "applicantRef" -> value;
            default -> number(value);
        });
        MvcResult result = api.evaluate(homefinToken(), body).andReturn();
        assertThat(result.getResponse().getStatus()).as(api.read(result).get("message").asText()).isEqualTo(expected);
        if (expected == 400) {
            assertThat(api.read(result).get("message").asText()).startsWith(field + ": ");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "productCode=HOUSING", "houseOwnership=TWO", "requestedAmount=1.5", "termMonths=360.5",
            "broken" })
    @DisplayName("[TC-EVAL-104] 없는 상품·주택 보유 값, 금액·기간의 소수, 깨진 JSON → 400 Malformed request body, 평가 기록 없음")
    void unreadableBody_badRequest(String change) throws Exception {
        long before = evaluationCount(jdbc);
        Object body;
        if (change.equals("broken")) {
            body = "{\"productCode\": \"DIDIMDOL\",";
        } else {
            Map<String, Object> map = body();
            String[] kv = change.split("=");
            map.put(kv[0], kv[1].matches("[0-9.]+") ? new java.math.BigDecimal(kv[1]) : kv[1]);
            body = map;
        }
        api.evaluate(homefinToken(), body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
        assertThat(evaluationCount(jdbc)).isEqualTo(before);
    }

    @Test
    @DisplayName("[TC-EVAL-105] 같은 입력을 두 번 평가 → 기록 2건(번호 다름), 판정·점수·근거·기준 버전 같음")
    void sameInputTwice_twoRecordsSameOutcome() throws Exception {
        Map<String, Object> body = body();
        // 근거가 PASS만이 아니도록 대출 금액을 한도 위로 (판정이 같은지 의미 있게 비교)
        body.put("requestedAmount", 250_000_000L);
        long before = evaluationCount(jdbc);
        JsonNode first = evaluateOk(finflowToken(), body);
        JsonNode second = evaluateOk(finflowToken(), body);

        assertThat(evaluationCount(jdbc)).isEqualTo(before + 2);
        assertThat(first.get("evaluationId")).isNotEqualTo(second.get("evaluationId"));
        assertThat(first.get("verdict").asText()).isEqualTo("INELIGIBLE");
        for (String field : List.of("verdict", "riskLevel", "riskScore", "recommendation", "criteriaVersion",
                "reasons")) {
            assertThat(second.get(field)).as(field).isEqualTo(first.get(field));
        }
    }

    @Test
    @DisplayName("[TC-EVAL-106] 평가 조회: 요청한 서버 / 운영자 → 200, 평가 응답과 같은 판정·근거·기준 버전, 입력값 칸 없음")
    void find_ownServerAndOperator() throws Exception {
        Map<String, Object> body = body();
        body.put("requestedAmount", 250_000_000L);
        JsonNode created = evaluateOk(finflowToken(), body);
        String path = "/api/evaluations/" + created.get("evaluationId").asText();

        for (String token : List.of(finflowToken(), api.operatorToken())) {
            JsonNode found = api.read(api.adminGet(token, path).andExpect(status().isOk()).andReturn()).get("data");
            assertThat(found).isEqualTo(created);
            assertThat(found.get("sourceSystem").asText()).isEqualTo("FINFLOW");
            assertThat(found.get("productCode").asText()).isEqualTo("DIDIMDOL");
            assertThat(found.get("applicantRef").asText()).isEqualTo("homefin:1");
            for (String input : List.of("householdAnnualIncome", "dti", "requestedAmount", "housePrice")) {
                assertThat(found.has(input)).as(input).isFalse();
            }
        }
    }

    @Test
    @DisplayName("[TC-EVAL-107] 평가 조회: 다른 서버가 요청한 평가 / 없는 번호 → 둘 다 404 같은 메시지 Evaluation not found")
    void find_otherServerOrMissing_notFound() throws Exception {
        String evaluationId = evaluateOk(finflowToken(), body()).get("evaluationId").asText();

        for (String path : List.of("/api/evaluations/" + evaluationId, "/api/evaluations/" + UUID.randomUUID())) {
            mockMvc.perform(get(path).header("Authorization", bearer(homefinToken())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Evaluation not found"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }
}
