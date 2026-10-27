package com.riskwatch.repository;

import com.riskwatch.domain.ProductCode;
import com.riskwatch.domain.SourceSystem;
import com.riskwatch.service.EvaluationService;
import com.riskwatch.support.SpecCriteria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 실제 MySQL 8.0에서 Flyway·스키마 검증·MySQL 동작 확인 (Testcontainers, Docker가 없으면 건너뜀)
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class MySqlSchemaTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0");

    // 데이터소스를 컨테이너 MySQL로, Hibernate는 엔티티와 실제 테이블이 맞는지 검사
    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EvaluationService evaluationService;

    @Test
    @DisplayName("[TC-EVAL-207] 실제 MySQL 8에서 Flyway V1·V2, 엔티티 확인 통과, 기준값→버전 외래키, (버전·상품·키) unique, 평가→기준 버전 외래키, 평가 저장")
    void schemaWorksOnMySql() {
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success = 1 order by installed_rank", String.class);
        assertThat(versions).containsExactly("1", "2");
        assertThat(jdbc.queryForObject("select count(*) from criteria_values", Long.class)).isEqualTo(32);

        Long versionId = jdbc.queryForObject("select id from criteria_versions where version = ?", Long.class,
                SpecCriteria.VERSION);
        // 없는 버전의 기준값은 거부
        assertThatThrownBy(() -> jdbc.update("insert into criteria_values (version_id, product_code, criteria_key,"
                + " criteria_value) values (987654, 'DIDIMDOL', 'LTV', 0.7)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 같은 버전·상품·키는 한 번만
        assertThatThrownBy(() -> jdbc.update("insert into criteria_values (version_id, product_code, criteria_key,"
                + " criteria_value) values (?, 'DIDIMDOL', 'LTV', 0.75)", versionId))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 평가 저장 (MySQL에서 엔티티·암호화·JSON 근거 저장)
        String evaluationId = evaluationService.evaluate(SpecCriteria.pass(ProductCode.NEWBORN_DIDIMDOL).build(),
                SourceSystem.FINFLOW).evaluationId();
        assertThat(jdbc.queryForObject("select verdict from risk_evaluations where evaluation_id = ?", String.class,
                evaluationId)).isEqualTo("ELIGIBLE");
        // 기준 버전이 평가에 쓰였으면 지울 수 없음
        assertThatThrownBy(() -> jdbc.update("delete from criteria_versions where id = ?", versionId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
