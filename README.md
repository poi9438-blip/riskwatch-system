# RiskWatch

정책대출 **심사 규칙 엔진**입니다. 신청 내용(소득, 주택 가격, 대출 금액, DTI, 가구 조건)을 상품별 심사 기준에 비춰 **적격 / 검토 필요 / 부적격**으로 판정하고, 규칙마다 근거(기준값, 실제값, 충족 여부)를 함께 남깁니다.

직접 만든 FinSight 구성의 RiskWatch(거래·정산 위험 점수)를 처음부터 다시 설계한 저장소입니다. 원본과 달라진 점은 [docs/known-issues.md](docs/known-issues.md)에 있습니다.

## 함께 쓰는 저장소

세 저장소를 같은 폴더에 나란히 받으면 HomeFin Mate에서 세 서비스를 함께 띄울 수 있습니다 ([HomeFin 실행 5번](https://github.com/poi9438-blip/homefin-mate#5-세-서비스-함께-실행-선택)).

| 저장소                                                               | 역할                                                                  | 포트             |
| -------------------------------------------------------------------- | --------------------------------------------------------------------- | ---------------- |
| [homefin-mate](https://github.com/poi9438-blip/homefin-mate)         | 사용자 화면·상담(대출 계산, 정책 추천, 통합 분석, 신청), 관측 compose | 8100 (화면 3100) |
| [finflow-engine](https://github.com/poi9438-blip/finflow-engine)     | 대출 신청 접수·상태 관리·배치                                         | 8110             |
| [riskwatch-system](https://github.com/poi9438-blip/riskwatch-system) | 심사 규칙 엔진 (판정·근거·기준 버전)                                  | 8120             |

## 서비스 구성에서의 위치

```
사용자 ─▶ HomeFin Mate (:8100) ──서비스 토큰·X-Trace-Id──▶ RiskWatch (:8120)  상담 단계 사전 판정
                 │                                              ▲
                 └──▶ FinFlow (:8110) ── 심사 시작 때 ──────────┘  신청 심사 판정 (기준 버전과 함께 기록)
                                                                ▲
                                           운영자 (ADMIN: 심사 기준 변경·이력)
```

- **기준표는 DB에 있습니다.** 운영자가 새 기준 버전을 만들면 그 시각부터 적용되고, 모든 판정에는 어느 기준 버전으로 판정했는지 남습니다.
- **traceId:** 받은 `X-Trace-Id`를 그대로 씁니다. 헤더가 없으면 `rw-UUID`를 만듭니다.
- **서비스 토큰:** HomeFin과 FinFlow는 비밀키가 서로 다르고(`HOMEFIN_TOKEN_SECRET`, `FINFLOW_TOKEN_SECRET`), 평가 기록의 요청 서버는 토큰 발급자로 정합니다. 각 서버는 자기가 요청한 평가만 조회할 수 있습니다.

## 진행 단계

| 단계 | 내용                                                                     | 상태 |
| ---- | ------------------------------------------------------------------------ | ---- |
| 1    | 뼈대: 공통 응답·오류 형식, traceId, 보안 기본값, compose, CI + 업무 명세 | 완료 |
| 2    | 심사 규칙 엔진 + DB 기준표(첫 기준 버전) + 평가 기록                     | 완료 |
| 3    | 운영자 계정, 심사 기준 변경 API(새 버전, 이력)                           | 완료 |
| 4    | 평가 API(서비스 토큰) + HomeFin·FinFlow 연결                             | 완료 |
| 5    | 관측: 요청 span에 업무 traceId(`hfm.trace_id`), OTel 에이전트 이미지     | 완료 |
| 6    | 업무 지표: 평가 API 평가 수(요청 서버·상품·판정별)                       | 완료 |

## 실행

### 0. 환경변수

```bash
cp .env.example .env   # 비밀번호를 직접 정한 값으로 바꾸기
```

### 1. Docker Compose

```bash
docker compose up -d --build
curl http://localhost:8120/actuator/health   # {"status":"UP"}
```

| 항목  | 주소                  |
| ----- | --------------------- |
| 서버  | http://localhost:8120 |
| MySQL | 127.0.0.1:3312        |

- **시작 준비:** 띄운 직후 첫 평가가 느리지 않게 시작할 때 평가 경로를 한 번 실행하고(저장은 롤백) 준비가 끝난 뒤 health가 `UP`이 됩니다 (known-issues #2, `WARM_UP_ENABLED=false`로 끔).

| API                                          | 주체          | 내용                                         |
| -------------------------------------------- | ------------- | -------------------------------------------- |
| `POST /api/auth/login`                       | 누구나        | 운영자 로그인                                |
| `POST /api/evaluations`                      | SERVICE       | 평가 (HomeFin·FinFlow 서비스 토큰, 201)      |
| `GET /api/evaluations/{evaluationId}`        | SERVICE·ADMIN | 평가 조회 (서비스는 자기가 요청한 평가만)    |
| `GET /api/admin/criteria`                    | ADMIN         | 지금 적용 중인 기준                          |
| `GET /api/admin/criteria/versions`           | ADMIN         | 기준 버전 목록 (최신순)                      |
| `GET /api/admin/criteria/versions/{version}` | ADMIN         | 버전 상세 (값 전체 + 이전 버전 대비 바뀐 값) |
| `POST /api/admin/criteria/versions`          | ADMIN         | 새 기준 버전 (적용 시각부터 판정에 씀)       |
| `GET /api/admin/evaluations`                 | ADMIN         | 평가 기록 목록 (상품·판정 필터, 페이지)      |

API 전체는 [업무 명세](docs/spec.md)에 있습니다.

### 2. 테스트

```bash
./mvnw test                   # Windows: mvnw.cmd test
python scripts/tc_status.py   # 테스트 명세와 결과 대조
```

### 3. 관측 (선택)

기본 이미지에는 관측 도구가 없습니다. Dockerfile의 `otel` 빌드 단계에 OpenTelemetry Java 에이전트(2.33.0, 체크섬 확인)가 들어 있고, HomeFin 저장소의 관측 compose가 이 단계를 씁니다.

```bash
# homefin-mate 폴더에서 (세 저장소를 나란히 받아 둔 상태)
docker compose -f docker-compose.yml -f full/docker-compose.full.yml -f observability/docker-compose.observability.yml -f observability/docker-compose.observability.full.yml up -d --build
```

- Grafana(http://localhost:3000) → Tempo에서 `{span.hfm.trace_id="…"}`로 찾으면 RiskWatch span이 HomeFin·FinFlow span과 한 추적으로 보입니다. 속성 이름은 세 서비스가 같습니다.
- Loki에서 `{service_name="riskwatch"} | traceId="…"`로 같은 번호의 로그를 찾습니다.
- 업무 지표: `riskwatch_evaluations_total{source_system, product_code, verdict}`(커밋된 평가만, 시작 준비는 세지 않음). HomeFin 대시보드 "FinSight 서비스"가 씁니다.

## 문서

- 업무 명세 (규칙·기준표·판정·API·데이터): [docs/spec.md](docs/spec.md)
- 테스트 기준·명세: [docs/TESTING.md](docs/TESTING.md), [docs/test-cases/](docs/test-cases/README.md)
- 원본에서 바꾼 것·문제 목록: [docs/known-issues.md](docs/known-issues.md)
