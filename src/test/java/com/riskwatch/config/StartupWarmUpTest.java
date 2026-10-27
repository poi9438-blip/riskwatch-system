package com.riskwatch.config;

import com.riskwatch.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// 시작 준비를 켜고 띄웠을 때: 평가 경로를 한 번 실행하고 평가 저장은 롤백 (#2)
@SpringBootTest(properties = "app.warm-up.enabled=true")
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class StartupWarmUpTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[TC-BASE-109] 시작 준비 켬: 평가 경로를 한 번 실행(traceId rw-warm-up, 끝 로그), 평가 기록 0건(롤백)")
    void warmUpRunsEvaluationAndRollsBack(CapturedOutput output) {
        // 시작할 때 남긴 로그 (평가 서비스까지 실행됐는지, 끝까지 갔는지)
        assertThat(output.getAll().lines()
                .filter(l -> l.contains("[traceId=" + StartupWarmUp.TRACE_ID + "]"))
                .filter(l -> l.contains("Evaluated evaluationId=")))
                .hasSize(1);
        assertThat(output.getAll()).contains("Warm-up finished in").doesNotContain("Warm-up skipped");
        assertThat(ApiTestSupport.evaluationCount(jdbc)).isZero();
    }
}
