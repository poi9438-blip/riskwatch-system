package com.riskwatch.security;

import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 서비스 토큰 비밀키 시작 검사 (서버 없이 생성자만)
class ServiceTokensTest {

    private static final String A = "Service-Token-Test-Secret-A-32bytes-long!!";
    private static final String B = "Service-Token-Test-Secret-B-32bytes-long!!";
    private static final String C = "Service-Token-Test-Secret-C-32bytes-long!!";

    @Test
    @DisplayName("[TC-AUTH-203] HomeFin·FinFlow·운영자 비밀키 중 같은 값이 있거나 32바이트보다 짧으면 시작 실패")
    void secrets_mustDifferAndBeLongEnough() {
        assertThatCode(() -> new ServiceTokens(A, B, C)).doesNotThrowAnyException();
        // 같은 값: HomeFin=FinFlow / HomeFin=운영자 / FinFlow=운영자
        for (String[] same : new String[][] { { A, A, C }, { A, B, A }, { A, B, B } }) {
            assertThatThrownBy(() -> new ServiceTokens(same[0], same[1], same[2]))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must all be different")
                    // 메시지에 비밀키 값이 없음
                    .hasMessageNotContaining(same[0]);
        }
        assertThatThrownBy(() -> new ServiceTokens("short-homefin-secret", B, C)).isInstanceOf(WeakKeyException.class);
        assertThatThrownBy(() -> new ServiceTokens(A, "short-finflow-secret", C)).isInstanceOf(WeakKeyException.class);
    }
}
