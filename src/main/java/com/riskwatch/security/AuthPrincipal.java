package com.riskwatch.security;

// 인증된 호출 주체 (역할, 기록에 남길 주체 id: operator:{id}). 서비스 토큰(HomeFin·FinFlow)은 4단계에서 추가
public record AuthPrincipal(Role role, String actorId) {

    public static AuthPrincipal operator(Long operatorId) {
        return new AuthPrincipal(Role.ADMIN, "operator:" + operatorId);
    }
}
