# RiskWatch

정책대출 **심사 규칙 엔진**입니다. 신청 내용(소득, 주택 가격, 대출 금액, DTI, 가구 조건)을 상품별 심사 기준에 비춰 **적격 / 검토 필요 / 부적격**으로 판정하고, 규칙마다 근거(기준값, 실제값, 충족 여부)를 함께 남깁니다.

직접 만든 FinSight 구성의 RiskWatch(거래·정산 위험 점수)를 처음부터 다시 설계한 저장소입니다. 원본과 달라진 점은 [docs/known-issues.md](docs/known-issues.md)에 있습니다.

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

| 단계 | 내용                                                                     | 상태                                            |
| ---- | ------------------------------------------------------------------------ | ----------------------------------------------- |
| 1    | 뼈대: 공통 응답·오류 형식, traceId, 보안 기본값, compose, CI + 업무 명세 | 완료                                            |
| 2    | 심사 규칙 엔진 + DB 기준표(첫 기준 버전) + 평가 기록                     | 완료                                            |
| 3    | 운영자 계정, 심사 기준 변경 API(새 버전, 이력)                           | 완료                                            |
| 4    | 평가 API(서비스 토큰) + HomeFin·FinFlow 연결                             | 진행 중 (평가 API 완료, 연결은 FinFlow·HomeFin) |

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

### 2. 테스트

```bash
./mvnw test                   # Windows: mvnw.cmd test
python scripts/tc_status.py   # 테스트 명세와 결과 대조
```

## 문서

- 업무 명세 (규칙·기준표·판정·API·데이터): [docs/spec.md](docs/spec.md)
- 테스트 기준·명세: [docs/TESTING.md](docs/TESTING.md), [docs/test-cases/](docs/test-cases/README.md)
- 원본에서 바꾼 것·문제 목록: [docs/known-issues.md](docs/known-issues.md)
