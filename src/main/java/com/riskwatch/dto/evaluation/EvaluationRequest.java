package com.riskwatch.dto.evaluation;

import com.riskwatch.domain.HouseOwnership;
import com.riskwatch.domain.ProductCode;
import com.riskwatch.rule.EvaluationInput;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// 평가 요청 (업무 명세 3장). 판정 입력은 모두 필수, 요청 서버는 받지 않음(토큰으로 정함)
// 상품·주택 보유에 없는 값, 금액·기간의 소수는 본문을 읽을 때 거부 (400 Malformed request body)
public record EvaluationRequest(
        @NotNull(message = "Product code is required")
        ProductCode productCode,

        @NotNull(message = "Applicant ref is required")
        @Pattern(regexp = "homefin:[0-9]{1,30}", message = "Applicant ref must be homefin: followed by digits")
        String applicantRef,

        // 요청한 쪽의 참조 (FinFlow 신청 번호, HomeFin 계산 기록 id)
        @Size(max = 64, message = "Source ref must be at most 64 characters")
        String sourceRef,

        @NotNull(message = "Requested amount is required")
        @Min(value = 1, message = "Requested amount must be at least 1")
        @Max(value = 1_000_000_000L, message = "Requested amount must be at most 1000000000")
        Long requestedAmount,

        @NotNull(message = "Term months is required")
        @Min(value = 12, message = "Term months must be at least 12")
        @Max(value = 600, message = "Term months must be at most 600")
        Integer termMonths,

        @NotNull(message = "House price is required")
        @Min(value = 1, message = "House price must be at least 1")
        Long housePrice,

        // 부부합산 연소득 (원)
        @NotNull(message = "Household annual income is required")
        @Min(value = 0, message = "Household annual income must be 0 or more")
        Long householdAnnualIncome,

        @NotNull(message = "DTI is required")
        @DecimalMin(value = "0", message = "DTI must be 0 or more")
        @DecimalMax(value = "9.9999", message = "DTI must be at most 9.9999")
        @Digits(integer = 1, fraction = 4, message = "DTI must have at most 4 decimal places")
        BigDecimal dti,

        @NotNull(message = "House ownership is required")
        HouseOwnership houseOwnership,

        @NotNull(message = "First home is required")
        Boolean firstHome,

        @NotNull(message = "Newlywed is required")
        Boolean newlywed,

        @NotNull(message = "Newborn within 2 years is required")
        Boolean newbornWithin2Years,

        @NotNull(message = "Dual income is required")
        Boolean dualIncome,

        @NotNull(message = "Child count is required")
        @Min(value = 0, message = "Child count must be 0 or more")
        @Max(value = 20, message = "Child count must be at most 20")
        Integer childCount,

        // 시·도 이름 (비우면 수도권일 수 있어 생애최초 LTV 우대 없음)
        @Size(max = 20, message = "Region must be at most 20 characters")
        String region) {

    // 검증을 통과한 요청 → 규칙 엔진 입력
    public EvaluationInput toInput() {
        return new EvaluationInput(productCode, applicantRef, sourceRef, requestedAmount, termMonths, housePrice,
                householdAnnualIncome, dti, houseOwnership, firstHome, newlywed, newbornWithin2Years, dualIncome,
                childCount, region);
    }
}
