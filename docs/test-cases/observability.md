# 관측 · 추적 (TC-OBS)

대상: `TraceIdFilter`(요청 span 속성), `EvaluationMetrics`·`AfterCommit`(업무 지표), `Dockerfile`의 `otel` 단계(OpenTelemetry Java 에이전트)

**현재 구현**

- **두 번호를 함께 씁니다.**
  - 업무 traceId: 부른 서비스가 보낸 `hfm-…`·`ff-…` 또는 RiskWatch가 만든 `rw-…`입니다. 응답·평가 기록·로그(MDC)에 남습니다([base.md](base.md)).
  - OTel trace id: 관측용입니다. 에이전트가 만들고, HomeFin·FinFlow가 부를 때 `traceparent` 헤더로 이어집니다.
  - 요청 span에 `hfm.trace_id` 속성으로 업무 traceId를 붙입니다. HomeFin·FinFlow와 같은 속성 이름이라 Grafana(Tempo)에서 속성 하나로 세 서비스를 함께 찾습니다.
- **업무 지표:** `riskwatch.evaluations`(평가 API로 저장된 평가 수, 속성 `source_system`·`product_code`·`verdict`). 평가 기록이 커밋된 뒤에만 세고, 시작 준비(롤백)는 세지 않습니다. Prometheus 이름은 `riskwatch_evaluations_total`입니다. 정해진 조합은 시작할 때 0으로 만들어 둡니다(처음 생긴 값은 `increase()`가 증가로 보지 않아 알림이 첫 건을 놓치므로).
- **관측은 선택입니다.** 기본 이미지(`runtime`)에는 에이전트가 없고, 에이전트가 없으면 span 속성 코드는 아무 일도 하지 않습니다. 관측 compose(HomeFin 저장소 `observability/`)를 함께 띄울 때만 `otel` 단계를 씁니다.
- **span에 남기지 않는 것:** DB 쿼리는 값 대신 `?`로 남습니다. 요청·응답 본문과 Authorization 헤더는 기록하지 않습니다(에이전트 기본값).

| ID         | 분류 | 확인 내용                                                          | 기대 결과                                                                                                                                                | 위험 | 자동화                                                                           | 관련 |
| ---------- | ---- | ------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- | -------------------------------------------------------------------------------- | ---- |
| TC-OBS-101 | 정상 | 시작 준비 켬으로 띄운 뒤 평가 API로 HomeFin 1번·FinFlow 1번 평가   | `riskwatch.evaluations`가 요청 서버·상품·판정별로 1씩 증가, 시작 준비 평가(롤백)는 세지 않음, 다른 조합은 시작부터 0(요청 서버 2 × 상품 3 × 판정 3)      | P2   | EvaluationMetricsApiTest                                                         |      |
| TC-OBS-201 | 정상 | 요청 span 안에서 필터 실행: `X-Trace-Id: hfm-…` 있음 / 없음        | span 속성 `hfm.trace_id`가 받은 값 / 만든 `rw-` 값, 응답 헤더와 같은 값                                                                                  | P2   | TraceIdFilterSpanTest                                                            |      |
| TC-OBS-202 | 예외 | 트랜잭션 안에서 지표 기록: 커밋 / 롤백 / 트랜잭션 밖               | 커밋 뒤에만 셈 / 롤백이면 세지 않음 / 밖이면 바로 셈                                                                                                     | P2   | AfterCommitTest                                                                  |      |
| TC-OBS-301 | 정상 | HomeFin 관측·통합 compose에서 통합 분석 1번, FinFlow 심사 시작 1번 | 통합 분석: HomeFin·RiskWatch span이 한 추적. 심사 시작: FinFlow·RiskWatch span이 한 추적. 둘 다 `hfm.trace_id`로 찾고, Loki에서 traceId로 RiskWatch 로그 | P2   | 자동화 안 함: HomeFin 저장소의 관측 compose가 필요 (HomeFin TC-OBS-302에서 확인) |      |
