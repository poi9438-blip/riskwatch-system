package com.riskwatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

import java.util.TimeZone;

// RiskWatch 시작점 (정책대출 심사 규칙 엔진), 자동 생성 로그인 계정 끔
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class RiskwatchApplication {
    public static void main(String[] args) {
        // 서버 시각 기준을 UTC로 고정 (평가 기록 시각이 PC 시간대에 따라 바뀌지 않도록, HomeFin과 같음)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(RiskwatchApplication.class, args);
    }
}
