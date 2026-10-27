package com.riskwatch.security;

import com.riskwatch.domain.SourceSystem;

// 인증된 호출 주체 (역할, 기록에 남길 주체 id, 서비스면 요청 서버)
// 운영자: ADMIN, operator:{id} / 서비스: SERVICE, service:homefin·service:finflow
public record AuthPrincipal(Role role, String actorId, SourceSystem sourceSystem) {

    public static AuthPrincipal operator(Long operatorId) {
        return new AuthPrincipal(Role.ADMIN, "operator:" + operatorId, null);
    }

    public static AuthPrincipal service(SourceSystem sourceSystem) {
        return new AuthPrincipal(Role.SERVICE, "service:" + sourceSystem.name().toLowerCase(), sourceSystem);
    }
}
