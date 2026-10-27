package com.riskwatch.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// HTTP 통합 테스트 공통 도우미 (운영자 로그인, 운영자 API 호출)
public final class ApiTestSupport {

    // application-test.yml의 테스트 전용 값
    public static final String OPERATOR_SECRET = "RiskWatch-Test-Operator-JWT-Secret-32bytes-or-more";
    public static final String OPERATOR_EMAIL = "operator@test.local";
    public static final String OPERATOR_PASSWORD = "Operator-Test-Pass!1";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public ApiTestSupport(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    // 운영자 토큰 직접 만들기 (비밀키·발급 시각을 바꿔 가며 확인)
    public static String operatorToken(String secret, long operatorId, Instant issuedAt, long lifetimeSeconds) {
        return Jwts.builder()
                .issuer("riskwatch")
                .subject(String.valueOf(operatorId))
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(lifetimeSeconds)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    public MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password)))).andReturn();
    }

    // 첫 운영자로 로그인 → 토큰
    public String operatorToken() throws Exception {
        return read(login(OPERATOR_EMAIL, OPERATOR_PASSWORD)).at("/data/token").asText();
    }

    public ResultActions adminGet(String token, String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", bearer(token)));
    }

    public ResultActions adminPost(String token, String path, Object body) throws Exception {
        return mockMvc.perform(post(path).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    public String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    public JsonNode read(MvcResult result) throws Exception {
        // JSON은 UTF-8 (MockMvc 기본값 ISO-8859-1로 읽으면 한국어가 깨짐)
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    // 테스트가 만든 기준 버전과 그 버전으로 한 평가를 지움 (같은 DB를 쓰는 다른 테스트가 첫 버전으로 판정하도록)
    public static void deleteCreatedVersions(JdbcTemplate jdbc) {
        String others = "(select id from criteria_versions where version <> '" + SpecCriteria.VERSION + "')";
        jdbc.update("delete from risk_evaluations where criteria_version_id in " + others);
        jdbc.update("delete from criteria_values where version_id in " + others);
        jdbc.update("delete from criteria_versions where version <> ?", SpecCriteria.VERSION);
    }
}
