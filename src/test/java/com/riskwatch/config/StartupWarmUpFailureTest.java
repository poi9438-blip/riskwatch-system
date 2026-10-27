package com.riskwatch.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskwatch.common.BusinessException;
import com.riskwatch.common.TraceIds;
import com.riskwatch.domain.SourceSystem;
import com.riskwatch.security.ServiceTokens;
import com.riskwatch.service.EvaluationService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 시작 준비가 실패해도 서버 시작은 막지 않음 (#2)
class StartupWarmUpFailureTest {

    @Test
    @DisplayName("[TC-BASE-203] 시작 준비 중 평가 오류(적용 중인 기준 없음 등): 예외를 밖으로 던지지 않음, 로그용 traceId 정리")
    void failureDoesNotStopStartup() {
        EvaluationService evaluationService = mock(EvaluationService.class);
        when(evaluationService.evaluate(any(), eq(SourceSystem.HOMEFIN)))
                .thenThrow(new BusinessException(HttpStatus.CONFLICT, "No criteria in effect"));
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        StartupWarmUp warmUp = new StartupWarmUp(new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator(), mock(ServiceTokens.class),
                evaluationService, transactionManager);

        assertThatCode(() -> warmUp.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
        // 평가까지 가서 실패했고, 롤백했고, 다음 로그에 rw-warm-up이 남지 않음
        verify(evaluationService).evaluate(any(), eq(SourceSystem.HOMEFIN));
        verify(transactionManager).rollback(any());
        assertThat(MDC.get(TraceIds.MDC_KEY)).isNull();
    }
}
