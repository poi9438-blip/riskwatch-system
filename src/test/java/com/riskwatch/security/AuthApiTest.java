package com.riskwatch.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskwatch.entity.Operator;
import com.riskwatch.repository.OperatorRepository;
import com.riskwatch.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.riskwatch.support.ApiTestSupport.bearer;
import static com.riskwatch.support.ApiTestSupport.operatorToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 운영자 로그인·토큰 통합 테스트 (보안 필터 포함, FinFlow와 같은 방식)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    OperatorRepository operatorRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    OperatorTokens operatorTokens;
    @Autowired
    JdbcTemplate jdbc;

    private ApiTestSupport api;

    @BeforeEach
    void setUp() {
        api = new ApiTestSupport(mockMvc, objectMapper);
    }

    // 테스트마다 새 운영자 (로그인 실패 횟수가 다른 테스트와 섞이지 않게)
    private String newOperator(String password) {
        String email = "op-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        operatorRepository.save(Operator.builder().email(email).passwordHash(passwordEncoder.encode(password))
                .createdAt(LocalDateTime.now()).build());
        return email;
    }

    @Test
    @DisplayName("[TC-AUTH-101] 운영자 로그인 성공 → 200 {token, expiresAt}, 응답에 비밀번호·해시 없음, 그 토큰으로 운영자 API 200")
    void login_success() throws Exception {
        String email = newOperator("Right-Pass!1");
        MvcResult result = api.login(email, "Right-Pass!1");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = api.read(result).get("data");
        assertThat(Instant.parse(data.get("expiresAt").asText())).isAfter(Instant.now());
        assertThat(result.getResponse().getContentAsString()).doesNotContain("password").doesNotContain("$2a$");
        api.adminGet(data.get("token").asText(), "/api/admin/criteria").andExpect(status().isOk());
    }

    @Test
    @DisplayName("[TC-AUTH-102] 틀린 비밀번호 / 없는 이메일 → 둘 다 401 같은 메시지 Invalid credentials")
    void login_failure_sameMessage() throws Exception {
        String email = newOperator("Right-Pass!1");
        for (MvcResult r : List.of(api.login(email, "Wrong-Pass!1"),
                api.login("nobody-" + UUID.randomUUID() + "@test.local", "Right-Pass!1"))) {
            assertThat(r.getResponse().getStatus()).isEqualTo(401);
            assertThat(api.read(r).get("message").asText()).isEqualTo("Invalid credentials");
        }
    }

    @Test
    @DisplayName("[TC-AUTH-103] 같은 이메일로 5번 실패한 뒤 6번째는 맞는 비밀번호여도 429 (5번째까지 401)")
    void login_lockedAfterFiveFailures() throws Exception {
        String email = newOperator("Right-Pass!1");
        for (int i = 1; i <= 5; i++) {
            assertThat(api.login(email, "Wrong-Pass!" + i).getResponse().getStatus()).as("실패 %d번째", i).isEqualTo(401);
        }
        MvcResult sixth = api.login(email, "Right-Pass!1");
        assertThat(sixth.getResponse().getStatus()).isEqualTo(429);
        assertThat(api.read(sixth).get("message").asText()).isEqualTo("Too many login attempts. Try again later");
    }

    @Test
    @DisplayName("[TC-AUTH-104] 운영자 API를 토큰 없이 / 다른 비밀키로 서명한 토큰 / 만료된 토큰으로 → 모두 401 + traceId")
    void adminApi_invalidTokens_unauthorized() throws Exception {
        long operatorId = operatorRepository.findByEmail(ApiTestSupport.OPERATOR_EMAIL).orElseThrow().getId();
        mockMvc.perform(get("/api/admin/criteria").header("X-Trace-Id", "hfm-auth-104"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.traceId").value("hfm-auth-104"));
        for (String token : List.of(
                operatorToken("Some-Other-Secret-Key-That-Is-32bytes-long!!", operatorId, Instant.now(), 300),
                operatorToken(ApiTestSupport.OPERATOR_SECRET, operatorId, Instant.now().minusSeconds(600), 300))) {
            mockMvc.perform(get("/api/admin/criteria").header("Authorization", bearer(token)))
                    .andExpect(status().isUnauthorized());
        }
        // 맞는 토큰은 통과 (위 401이 토큰 때문임을 확인)
        mockMvc.perform(get("/api/admin/criteria").header("Authorization",
                bearer(operatorToken(ApiTestSupport.OPERATOR_SECRET, operatorId, Instant.now(), 300))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("[TC-AUTH-105] 유효한 운영자 토큰이라도 DB에서 운영자가 삭제됐으면 401")
    void deletedOperatorToken_unauthorized() throws Exception {
        String email = newOperator("Right-Pass!1");
        Long id = operatorRepository.findByEmail(email).orElseThrow().getId();
        String token = operatorTokens.issue(id).token();
        operatorRepository.deleteById(id);

        api.adminGet(token, "/api/admin/criteria").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[TC-AUTH-202] 로그인 실패 기록의 이메일은 HMAC 값으로만 저장 (원문 없음)")
    void loginFailure_storesHmacOnly() throws Exception {
        String email = newOperator("Right-Pass!1");
        api.login(email, "Wrong-Pass!1");

        List<String> hashes = jdbc.queryForList("select email_hash from operator_login_failures", String.class);
        assertThat(hashes).isNotEmpty().allMatch(h -> h.matches("hmac:[0-9a-f]{64}"));
        assertThat(jdbc.queryForObject("select count(*) from operator_login_failures where email_hash like ?",
                Long.class, "%" + email + "%")).isZero();
    }
}
