package io.github.matrixlane.metadataregistry.web.service.identifier;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierInputMode;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierStrategyConfigService;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.SemVerValidator;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.VersioningPolicy;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Enforces the namespace-scoped identifier input policy.
 *
 * <ul>
 *   <li>{@code AUTO_ONLY}: manual identifier rejected; blank identifier generated.</li>
 *   <li>{@code AUTO_WITH_OVERRIDE}: blank identifier generated; manual identifier validated.</li>
 *   <li>{@code MANUAL_ONLY}: blank identifier rejected; manual identifier validated.</li>
 * </ul>
 *
 * <p>Versions follow their own independent input mode: AUTO modes generate the
 * initial version per the namespace {@link VersioningPolicy} when blank;
 * {@code MANUAL_ONLY} requires an explicit, policy-valid version.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentifierInputPolicyService {

    private static final Pattern SEQUENTIAL_VERSION = Pattern.compile("^(0|[1-9]\\d*)$");
    private static final Pattern DATE_VERSION = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private final NamespaceIdentifierPolicyResolver policyResolver;
    private final IdentifierStrategyConfigService identifierStrategyConfigService;

    /**
     * Resolves the identifier to persist for a creation request.
     *
     * @param namespace target namespace (may be null, meaning default policy)
     * @param requested identifier supplied by the caller (may be null/blank)
     * @return the identifier to use (generated or validated manual value)
     */
    public String resolveIdentifier(Namespace namespace, String requested) {
        return resolveIdentifier(namespace, requested, null);
    }

    /**
     * Resolves the identifier to persist for a creation request, carrying the
     * registered item's type (class simple name) so template/sequence strategies
     * can substitute the {@code {type}} placeholder.
     */
    public String resolveIdentifier(Namespace namespace, String requested, String itemType) {
        NamespaceIdentifierPolicy policy = policyResolver.resolve(namespace);
        boolean blank = requested == null || requested.isBlank();
        IdentifierStrategyConfigService.GenerationContext context =
                IdentifierStrategyConfigService.GenerationContext.ofItemType(itemType);

        switch (policy.inputMode()) {
            case AUTO_ONLY -> {
                if (!blank) {
                    throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                            "该命名空间的标识符输入模式为 AUTO_ONLY，禁止手动指定标识符");
                }
                return identifierStrategyConfigService.generateIdentifier(namespace, context);
            }
            case MANUAL_ONLY -> {
                if (blank) {
                    throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                            "该命名空间的标识符输入模式为 MANUAL_ONLY，已禁用自动生成，请手动填写标识符");
                }
                validateManualIdentifier(policy, requested);
                return requested;
            }
            default -> { // AUTO_WITH_OVERRIDE
                if (blank) {
                    return identifierStrategyConfigService.generateIdentifier(namespace, context);
                }
                validateManualIdentifier(policy, requested);
                return requested;
            }
        }
    }

    /**
     * Resolves the version to persist for a creation request.
     *
     * @param namespace target namespace (may be null, meaning default policy)
     * @param requested version supplied by the caller (may be null/blank)
     * @return the version to use (generated initial version or validated manual value)
     */
    public String resolveVersion(Namespace namespace, String requested) {
        NamespaceIdentifierPolicy policy = policyResolver.resolve(namespace);
        boolean blank = requested == null || requested.isBlank();
        if (blank) {
            if (policy.versionInputMode() == IdentifierInputMode.MANUAL_ONLY) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "该命名空间的版本输入模式为 MANUAL_ONLY，版本号必填");
            }
            return initialVersion(policy.versioningPolicy());
        }
        if (policy.versionInputMode() == IdentifierInputMode.AUTO_ONLY) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "该命名空间的版本输入模式为 AUTO_ONLY，禁止手动指定版本号");
        }
        validateVersion(policy, requested);
        return requested;
    }

    /**
     * Validates an explicitly supplied (manual) version against the
     * namespace's versioning policy and version input mode.
     * Used on version changes as well.
     */
    public void validateManualVersion(Namespace namespace, String version) {
        NamespaceIdentifierPolicy policy = policyResolver.resolve(namespace);
        if (policy.versionInputMode() == IdentifierInputMode.AUTO_ONLY) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "该命名空间的版本输入模式为 AUTO_ONLY，禁止手动指定版本号");
        }
        validateVersion(policy, version);
    }

    private String initialVersion(VersioningPolicy policy) {
        return switch (policy) {
            case SEMVER -> "1.0.0";
            case SEQUENTIAL -> "1";
            case DATE -> LocalDate.now().toString();
        };
    }

    private void validateManualIdentifier(NamespaceIdentifierPolicy policy, String identifier) {
        String pattern = policy.manualIdentifierPattern();
        if (pattern == null) {
            return;
        }
        Pattern compiled = compilePattern(pattern);
        if (compiled != null && !compiled.matcher(identifier).matches()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "标识符不符合该命名空间的手动标识校验规则: " + pattern);
        }
    }

    private void validateVersion(NamespaceIdentifierPolicy policy, String version) {
        switch (policy.versioningPolicy()) {
            case SEQUENTIAL -> {
                if (!SEQUENTIAL_VERSION.matcher(version).matches()) {
                    throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                            "版本号不符合顺序整数版本规则（如 1、2、3）: " + version);
                }
            }
            case DATE -> {
                if (!DATE_VERSION.matcher(version).matches() || !isValidDate(version)) {
                    throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                            "版本号不符合日期版本规则（yyyy-MM-dd）: " + version);
                }
            }
            default -> {
                try {
                    SemVerValidator.validate(version);
                } catch (IllegalArgumentException e) {
                    throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                            "版本号不符合语义化版本规则（major.minor.patch）: " + version);
                }
            }
        }
    }

    private boolean isValidDate(String version) {
        try {
            LocalDate.parse(version);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Pattern compilePattern(String pattern) {
        try {
            return Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            log.warn("[IdentifierInputPolicyService] Invalid manual identifier pattern '{}', skipping validation", pattern);
            return null;
        }
    }
}
