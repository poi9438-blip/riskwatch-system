package com.riskwatch.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskwatch.domain.ProductCode;
import com.riskwatch.support.ApiTestSupport;
import com.riskwatch.support.TestMetrics;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static com.riskwatch.support.ApiTestSupport.evaluationBody;
import static com.riskwatch.support.ApiTestSupport.finflowToken;
import static com.riskwatch.support.ApiTestSupport.homefinToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 업무 지표: 평가 API로 저장된 평가만 셈 (시작 준비를 켜고 띄워서 그 평가는 세지 않는지 함께 확인)
@SpringBootTest(properties = "app.warm-up.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestMetrics.class)
class EvaluationMetricsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InMemoryMetricReader metricReader;

    private String verdict(String token, ProductCode product) throws Exception {
        ApiTestSupport api = new ApiTestSupport(mockMvc, objectMapper);
        return api.read(api.evaluate(token, evaluationBody(product)).andExpect(status().isCreated()).andReturn())
                .get("data").get("verdict").asText();
    }

    private static Attributes attributes(String source, ProductCode product, String verdict) {
        return Attributes.of(EvaluationMetrics.SOURCE_SYSTEM, source, EvaluationMetrics.PRODUCT_CODE, product.name(),
                EvaluationMetrics.VERDICT, verdict);
    }

    @Test
    @DisplayName("[TC-OBS-101] 평가 API 평가 HomeFin 1번·FinFlow 1번: riskwatch.evaluations가 요청 서버·상품·판정별로 1씩, 시작 준비 평가는 세지 않음, 다른 조합은 시작부터 0")
    void countsApiEvaluationsOnly() throws Exception {
        String homefin = verdict(homefinToken(), ProductCode.BOGEUMJARI);
        String finflow = verdict(finflowToken(), ProductCode.DIDIMDOL);

        assertThat(TestMetrics.count(metricReader, "riskwatch.evaluations",
                attributes("HOMEFIN", ProductCode.BOGEUMJARI, homefin))).isEqualTo(1);
        assertThat(TestMetrics.count(metricReader, "riskwatch.evaluations",
                attributes("FINFLOW", ProductCode.DIDIMDOL, finflow))).isEqualTo(1);
        // 시작 준비(HOMEFIN·BOGEUMJARI 평가 후 롤백)가 세졌다면 합이 3
        assertThat(TestMetrics.total(metricReader, "riskwatch.evaluations")).isEqualTo(2);
        // 기록하지 않은 조합도 0으로 있음 (Prometheus increase()가 첫 건을 잡게)
        assertThat(TestMetrics.series(metricReader, "riskwatch.evaluations")).isEqualTo(2 * 3 * 3);
    }
}
