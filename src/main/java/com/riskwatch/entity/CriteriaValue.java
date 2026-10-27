package com.riskwatch.entity;

import com.riskwatch.domain.ProductCode;
import jakarta.persistence.Column;
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

// 기준값 한 칸 (버전 × 상품 × 기준 키)
@Entity
@Table(name = "criteria_values")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CriteriaValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false, updatable = false)
    private Long versionId;

    // enum 칸은 VARCHAR로 (MySQL ENUM이 아님, FinFlow #1)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "product_code", nullable = false, updatable = false)
    private ProductCode productCode;

    @Column(name = "criteria_key", nullable = false, updatable = false)
    private String criteriaKey;

    @Column(name = "criteria_value", nullable = false, updatable = false, precision = 15, scale = 4)
    private BigDecimal value;
}
