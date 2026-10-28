package com.riskwatch.metrics;

import com.riskwatch.common.AfterCommit;
import com.riskwatch.domain.ProductCode;
import com.riskwatch.domain.SourceSystem;
import com.riskwatch.domain.Verdict;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import org.springframework.stereotype.Component;

// 업무 지표: 평가 API로 저장된 평가 수 (Prometheus 이름 riskwatch_evaluations_total)
@Component
public class EvaluationMetrics {

    static final String EVALUATIONS = "riskwatch.evaluations";
    static final AttributeKey<String> SOURCE_SYSTEM = AttributeKey.stringKey("source_system");
    static final AttributeKey<String> PRODUCT_CODE = AttributeKey.stringKey("product_code");
    static final AttributeKey<String> VERDICT = AttributeKey.stringKey("verdict");

    private final LongCounter evaluations;

    public EvaluationMetrics(OpenTelemetry openTelemetry) {
        this.evaluations = openTelemetry.getMeter("riskwatch").counterBuilder(EVALUATIONS)
                .setDescription("Evaluations saved through the evaluation API")
                .setUnit("{evaluation}")
                .build();
        // 정해진 조합을 0으로 미리 만듦: 처음 생긴 값은 Prometheus increase()가 증가로 보지 않아 첫 건을 놓치므로
        for (SourceSystem source : SourceSystem.values()) {
            for (ProductCode product : ProductCode.values()) {
                for (Verdict verdict : Verdict.values()) {
                    evaluations.add(0, attributes(source, product, verdict));
                }
            }
        }
    }

    private static Attributes attributes(SourceSystem source, ProductCode product, Verdict verdict) {
        return Attributes.of(SOURCE_SYSTEM, source.name(), PRODUCT_CODE, product.name(), VERDICT, verdict.name());
    }

    // 평가 기록이 커밋된 뒤에 셈 (롤백되면 세지 않음)
    public void evaluated(SourceSystem sourceSystem, ProductCode productCode, Verdict verdict) {
        Attributes attributes = attributes(sourceSystem, productCode, verdict);
        AfterCommit.run(() -> evaluations.add(1, attributes));
    }
}
