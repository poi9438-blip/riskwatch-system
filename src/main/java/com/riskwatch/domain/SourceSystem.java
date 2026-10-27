package com.riskwatch.domain;

// 평가를 요청한 서버 (서비스 토큰의 발급자로 정함, 요청 본문 값을 믿지 않음)
public enum SourceSystem {
    HOMEFIN,
    FINFLOW
}
