package com.riskwatch.service;

import com.riskwatch.common.BusinessException;
import com.riskwatch.domain.ProductCode;
import com.riskwatch.domain.Verdict;
import com.riskwatch.dto.criteria.NewVersionRequest;
import com.riskwatch.dto.criteria.VersionView;
import com.riskwatch.dto.evaluation.EvaluationListItem;
import com.riskwatch.entity.CriteriaValue;
import com.riskwatch.entity.CriteriaVersion;
import com.riskwatch.repository.CriteriaValueRepository;
import com.riskwatch.repository.CriteriaVersionRepository;
import com.riskwatch.repository.RiskEvaluationRepository;
import com.riskwatch.rule.CriteriaRules;
import com.riskwatch.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

// 운영자 기준표 조회·새 버전 만들기, 평가 기록 목록 (업무 명세 5장·7장)
@Slf4j
@Service
@RequiredArgsConstructor
public class CriteriaAdminService {

    public static final int MAX_PAGE_SIZE = 100;

    private final CriteriaVersionRepository versionRepository;
    private final CriteriaValueRepository valueRepository;
    private final RiskEvaluationRepository evaluationRepository;

    // 지금 적용 중인 버전 (값 전체 + 이전 버전 대비 바뀐 값)
    @Transactional(readOnly = true)
    public VersionView.Detail current() {
        return detail(active(now()));
    }

    // 버전 목록 (최신순, 상태 포함)
    @Transactional(readOnly = true)
    public List<VersionView.Summary> versions() {
        LocalDateTime now = now();
        CriteriaVersion active = active(now);
        return versionRepository.findAllByOrderByEffectiveFromDescIdDesc().stream()
                .map(v -> summary(v, active, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public VersionView.Detail version(String name) {
        return detail(versionRepository.findByVersion(name)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Criteria version not found")));
    }

    // 새 버전 만들기 (검증 → 잠금 → 예약·이름 중복 확인 → 지금 버전 복사 + 바꾼 값)
    @Transactional
    public VersionView.Detail create(NewVersionRequest request, AuthPrincipal actor) {
        LocalDateTime now = now();
        LocalDateTime effectiveFrom = request.effectiveFrom() == null ? now
                : request.effectiveFrom().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()
                        .truncatedTo(ChronoUnit.MICROS);
        if (effectiveFrom.isBefore(now)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "effectiveFrom must not be in the past");
        }

        CriteriaVersion active = versionRepository.lockById(active(now).getId()).orElseThrow();
        if (versionRepository.existsByEffectiveFromAfter(now)) {
            throw new BusinessException(HttpStatus.CONFLICT, "A scheduled criteria version already exists");
        }
        if (versionRepository.existsByVersion(request.version())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Criteria version already exists");
        }

        Map<ProductCode, Map<String, BigDecimal>> values = valuesOf(active.getId());
        Set<String> seen = new HashSet<>();
        for (NewVersionRequest.Change change : request.changes()) {
            ProductCode product = ProductCode.valueOf(change.productCode());
            CriteriaRules.invalid(product, change.key(), change.value()).ifPresent(reason -> {
                throw new BusinessException(HttpStatus.BAD_REQUEST, reason);
            });
            if (!seen.add(product + ":" + change.key())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Duplicate change " + product + " " + change.key());
            }
            BigDecimal before = values.get(product).get(change.key());
            if (before != null && before.compareTo(change.value()) == 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        product + " " + change.key() + " is already " + before.stripTrailingZeros().toPlainString());
            }
            values.get(product).put(change.key(), change.value());
        }

        CriteriaVersion created = versionRepository.save(CriteriaVersion.builder()
                .version(request.version())
                .effectiveFrom(effectiveFrom)
                .createdBy(actor.actorId())
                .reason(request.reason())
                .createdAt(now)
                .build());
        values.forEach((product, byKey) -> byKey.forEach((key, value) -> valueRepository.save(CriteriaValue.builder()
                .versionId(created.getId()).productCode(product).criteriaKey(key).value(value).build())));
        log.info("Criteria version created version={} effectiveFrom={} by={} changes={}", created.getVersion(),
                effectiveFrom, actor.actorId(), request.changes().size());
        return detail(created);
    }

    // 평가 기록 목록 (상품·판정 필터, 최신순, page는 0부터, size는 1~100)
    @Transactional(readOnly = true)
    public List<EvaluationListItem> evaluations(ProductCode productCode, Verdict verdict, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "page must be 0 or more and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        Map<Long, String> names = new HashMap<>();
        versionRepository.findAll().forEach(v -> names.put(v.getId(), v.getVersion()));
        return evaluationRepository.search(productCode, verdict, PageRequest.of(page, size)).stream()
                .map(e -> new EvaluationListItem(e.getEvaluationId(), e.getSourceSystem(), e.getSourceRef(),
                        e.getApplicantRef(), e.getProductCode(), e.getVerdict(), e.getRiskLevel(), e.getRiskScore(),
                        names.get(e.getCriteriaVersionId()), e.getCreatedAt()))
                .toList();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private CriteriaVersion active(LocalDateTime now) {
        return versionRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(now)
                .orElseThrow(() -> new IllegalStateException("No criteria version in effect at " + now));
    }

    private VersionView.Summary summary(CriteriaVersion v, CriteriaVersion active, LocalDateTime now) {
        VersionView.Status status = v.getId().equals(active.getId()) ? VersionView.Status.ACTIVE
                : v.getEffectiveFrom().isAfter(now) ? VersionView.Status.SCHEDULED : VersionView.Status.PAST;
        return new VersionView.Summary(v.getVersion(), status, v.getEffectiveFrom(), v.getCreatedBy(), v.getReason(),
                v.getCreatedAt());
    }

    // 상세: 값 전체와 바로 앞 버전(적용 순서 기준) 대비 바뀐 값
    private VersionView.Detail detail(CriteriaVersion version) {
        LocalDateTime now = now();
        List<CriteriaVersion> ordered = versionRepository.findAllByOrderByEffectiveFromDescIdDesc();
        int index = ordered.indexOf(version);
        CriteriaVersion previous = index >= 0 && index + 1 < ordered.size() ? ordered.get(index + 1) : null;

        Map<ProductCode, Map<String, BigDecimal>> values = valuesOf(version.getId());
        List<VersionView.ValueChange> changes = new ArrayList<>();
        if (previous != null) {
            Map<ProductCode, Map<String, BigDecimal>> before = valuesOf(previous.getId());
            values.forEach((product, byKey) -> byKey.forEach((key, after) -> {
                BigDecimal old = before.get(product).get(key);
                if (old == null || old.compareTo(after) != 0) {
                    changes.add(new VersionView.ValueChange(product, key, old, after));
                }
            }));
        }
        return new VersionView.Detail(summary(version, active(now), now), values,
                previous == null ? null : previous.getVersion(), changes);
    }

    private Map<ProductCode, Map<String, BigDecimal>> valuesOf(Long versionId) {
        Map<ProductCode, Map<String, BigDecimal>> values = new EnumMap<>(ProductCode.class);
        for (ProductCode product : ProductCode.values()) {
            values.put(product, new TreeMap<>());
        }
        valueRepository.findByVersionId(versionId)
                .forEach(
                        v -> values.get(v.getProductCode()).put(v.getCriteriaKey(), v.getValue().stripTrailingZeros()));
        return values;
    }
}
