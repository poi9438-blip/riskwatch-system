package com.riskwatch.security;

import com.riskwatch.repository.OperatorRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// Bearer 운영자 토큰 → ADMIN으로 인증 (DB에 운영자가 있을 때만), 아니면 인증하지 않음(→ 401)
// 서비스 토큰(HomeFin·FinFlow)은 4단계에서 이 필터에 추가
@RequiredArgsConstructor
public class AuthTokenFilter extends OncePerRequestFilter {

    private final OperatorTokens operatorTokens;
    private final OperatorRepository operatorRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            operatorTokens.operatorId(header.substring(7))
                    .filter(operatorRepository::existsById)
                    .map(AuthPrincipal::operator)
                    .ifPresent(principal -> SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(principal, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())))));
        }
        chain.doFilter(request, response);
    }
}
