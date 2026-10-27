package com.riskwatch.entity;

import com.riskwatch.domain.HouseOwnership;
import com.riskwatch.domain.ProductCode;
import com.riskwatch.domain.SourceSystem;
import com.riskwatch.domain.Verdict;
import com.riskwatch.util.EncryptedDecimalConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 평가 기록: 입력·판정·근거·기준 버전 (저장 후 바뀌지 않음, 업무 명세 6장)
@Entity
@Table(name = "risk_evaluations")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RiskEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evaluation_id", nullable = false, updatable = false)
    private String evaluationId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "source_system", nullable = false, updatable = false)
    private SourceSystem sourceSystem;

    @Column(name = "source_ref", updatable = false)
    private String sourceRef;

    @Column(name = "applicant_ref", nullable = false, updatable = false)
    private String applicantRef;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "product_code", nullable = false, updatable = false)
    private ProductCode productCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false)
    private Verdict verdict;

    @Column(name = "risk_level", nullable = false, updatable = false)
    private String riskLevel;

    @Column(name = "risk_score", nullable = false, updatable = false)
    private Integer riskScore;

    @Column(nullable = false, updatable = false)
    private String recommendation;

    @Column(name = "criteria_version_id", nullable = false, updatable = false)
    private Long criteriaVersionId;

    // 규칙별 근거 (JSON 배열)
    @Column(nullable = false, updatable = false)
    private String reasons;

    @Column(name = "requested_amount", nullable = false, updatable = false)
    private Long requestedAmount;

    @Column(name = "term_months", nullable = false, updatable = false)
    private Integer termMonths;

    @Column(name = "house_price", nullable = false, updatable = false)
    private Long housePrice;

    // 암호문으로 저장 (읽을 때 복호화)
    @Convert(converter = EncryptedDecimalConverter.class)
    @Column(name = "household_annual_income", nullable = false, updatable = false)
    private BigDecimal householdAnnualIncome;

    @Convert(converter = EncryptedDecimalConverter.class)
    @Column(nullable = false, updatable = false)
    private BigDecimal dti;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "house_ownership", nullable = false, updatable = false)
    private HouseOwnership houseOwnership;

    @Column(name = "first_home", nullable = false, updatable = false)
    private Boolean firstHome;

    @Column(nullable = false, updatable = false)
    private Boolean newlywed;

    @Column(name = "newborn_within_2_years", nullable = false, updatable = false)
    private Boolean newbornWithin2Years;

    @Column(name = "dual_income", nullable = false, updatable = false)
    private Boolean dualIncome;

    @Column(name = "child_count", nullable = false, updatable = false)
    private Integer childCount;

    @Column(updatable = false)
    private String region;

    @Column(name = "trace_id", updatable = false)
    private String traceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
