package com.riskwatch.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskwatch.common.TraceIds;
import com.riskwatch.domain.SourceSystem;
import com.riskwatch.dto.evaluation.EvaluationRequest;
import com.riskwatch.security.ServiceTokens;
import com.riskwatch.service.EvaluationService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 시작 준비 (#2): 띄운 직후 첫 평가가 느리지 않게 평가 경로를 한 번 미리 실행 (평가 저장은 롤백)
// ApplicationRunner는 준비 상태가 "요청 받음"이 되기 전에 실행됨 → 끝날 때까지 /actuator/health 503
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.warm-up.enabled", havingValue = "true", matchIfMissing = true)
public class StartupWarmUp implements ApplicationRunner {

    // 로그용 traceId (미리 실행의 로그를 실제 요청과 구분)
    static final String TRACE_ID = "rw-warm-up";

    // 미리 실행할 요청 (HomeFin 통합 분석과 같은 모양, 저장은 롤백)
    static final String SAMPLE_REQUEST = """
            {"productCode":"BOGEUMJARI","applicantRef":"homefin:0","sourceRef":"warm-up",
             "requestedAmount":300000000,"termMonths":360,"housePrice":600000000,"householdAnnualIncome":48000000,
             "dti":0.3801,"houseOwnership":"NONE","firstHome":true,"newlywed":false,"newbornWithin2Years":false,
             "dualIncome":false,"childCount":0,"region":"부산"}""";

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final ServiceTokens serviceTokens;
    private final EvaluationService evaluationService;
    private final PlatformTransactionManager transactionManager;

    @Override
    public void run(ApplicationArguments args) {
        long started = System.nanoTime();
        MDC.put(TraceIds.MDC_KEY, TRACE_ID);
        try {
            // 요청 본문 읽기·검증 (Jackson, Bean Validation 첫 실행)
            EvaluationRequest request = objectMapper.readValue(SAMPLE_REQUEST, EvaluationRequest.class);
            validator.validate(request);
            // 서비스 토큰 확인 (JWT 라이브러리 첫 실행, 틀린 토큰이라 결과 없음)
            serviceTokens.sourceSystem("warm.up.token");
            // 기준표 조회·규칙 엔진·평가 저장까지 실행하고 롤백 (평가 기록은 남지 않음)
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                evaluationService.evaluate(request.toInput(), SourceSystem.HOMEFIN);
                status.setRollbackOnly();
            });
            log.info("Warm-up finished in {}ms (evaluation rolled back)", (System.nanoTime() - started) / 1_000_000);
        } catch (Exception e) {
            // 미리 실행은 속도를 위한 것이라 실패해도 시작은 계속 (첫 요청만 느림)
            log.warn("Warm-up skipped: {}", e.getClass().getSimpleName());
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
        }
    }
}
