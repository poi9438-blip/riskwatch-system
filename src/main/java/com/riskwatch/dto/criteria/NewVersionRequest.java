package com.riskwatch.dto.criteria;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

// 새 기준 버전 요청 (바꾸는 값만, 나머지는 지금 버전에서 복사). 만든 사람은 토큰으로 정하므로 받지 않음
public record NewVersionRequest(
        @NotBlank(message = "Version is required")
        @Pattern(regexp = "[A-Za-z0-9._-]{1,30}", message = "Version must be 1 to 30 letters, digits or . _ -")
        String version,

        // 비우면 즉시 (시간대 포함, 예: 2026-11-01T00:00:00+09:00)
        OffsetDateTime effectiveFrom,

        @NotBlank(message = "Reason is required")
        @Size(max = 500, message = "Reason must be at most 500 characters")
        String reason,

        @NotEmpty(message = "Changes are required")
        List<@Valid Change> changes) {

    // 바꾸는 값 하나
    public record Change(
            @NotBlank(message = "Product code is required")
            @Pattern(regexp = "DIDIMDOL|NEWBORN_DIDIMDOL|BOGEUMJARI",
                    message = "Product code must be one of DIDIMDOL, NEWBORN_DIDIMDOL, BOGEUMJARI")
            String productCode,
            @NotBlank(message = "Key is required") String key,
            @NotNull(message = "Value is required") BigDecimal value) {
    }
}
