package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalStep;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalStepAssigneeRule;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalTemplate;
import io.github.matrixlane.metadataregistry.web.entity.RejectStrategy;
import io.github.matrixlane.metadataregistry.web.repository.ApprovalStepRepository;
import io.github.matrixlane.metadataregistry.web.repository.ApprovalTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 审批流程模板管理服务。
 *
 * <p>管理多级审批流程模板的创建、查询、启用/禁用和锁定。
 * 模板启用后自动锁定（只读），避免运行中实例受配置变更影响。</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ApprovalTemplateService {

    private final ApprovalTemplateRepository approvalTemplateRepository;
    private final ApprovalStepRepository approvalStepRepository;

    private static final int MAX_TIMEOUT_HOURS_7D = 24 * 7;
    private static final int[] VALID_TIMEOUT_HOURS = {24, 72, 168}; // 24h / 72h / 7d

    /**
     * 创建审批模板（含步骤定义）。
     *
     * @param template 模板实体
     * @param steps    步骤定义列表
     * @return 保存后的模板
     * @throws BusinessException 模板名称重复、步骤数不匹配、超时时间不合法
     */
    public ApprovalTemplate createTemplate(ApprovalTemplate template, List<ApprovalStep> steps) {
        if (approvalTemplateRepository.existsByName(template.getName())) {
            throw new BusinessException(ApiCodes.APPROVAL_TEMPLATE_NAME_DUPLICATE,
                    "审批模板名称已存在: " + template.getName());
        }

        validateSteps(steps, template.getStepCount());
        validateTimeout(template.getDefaultTimeoutHours());

        template.setEnabled(false);
        template.setLocked(false);

        ApprovalTemplate saved = approvalTemplateRepository.save(template);

        for (ApprovalStep step : steps) {
            step.setTemplate(saved);
            validateStepTimeout(step.getTimeoutHours());
            approvalStepRepository.save(step);
        }

        saved.setSteps(steps);
        return saved;
    }

    /**
     * 更新审批模板（仅未锁定模板可更新）。
     *
     * @param id       模板 ID
     * @param template 更新内容
     * @param steps    新的步骤定义（可选，为 null 则不更新步骤）
     * @return 更新后的模板
     * @throws BusinessException 模板不存在、已锁定、名称重复
     */
    public ApprovalTemplate updateTemplate(UUID id, ApprovalTemplate template, List<ApprovalStep> steps) {
        ApprovalTemplate existing = approvalTemplateRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ApiCodes.APPROVAL_TEMPLATE_NOT_FOUND,
                        "审批模板不存在: " + id));

        if (Boolean.TRUE.equals(existing.getLocked())) {
            throw new BusinessException(ApiCodes.APPROVAL_TEMPLATE_LOCKED,
                    "审批模板已锁定，无法修改: " + id);
        }

        if (!existing.getName().equals(template.getName())
                && approvalTemplateRepository.existsByName(template.getName())) {
            throw new BusinessException(ApiCodes.APPROVAL_TEMPLATE_NAME_DUPLICATE,
                    "审批模板名称已存在: " + template.getName());
        }

        existing.setName(template.getName());
        existing.setDescription(template.getDescription());
        existing.setStepCount(template.getStepCount());
        existing.setDefaultTimeoutHours(template.getDefaultTimeoutHours());
        existing.setDefaultRejectStrategy(template.getDefaultRejectStrategy());
        existing.setUpdatedAt(java.time.LocalDateTime.now());

        validateTimeout(existing.getDefaultTimeoutHours());

        if (steps != null && !steps.isEmpty()) {
            validateSteps(steps, existing.getStepCount());
            // 清除旧步骤
            existing.getSteps().clear();
            for (ApprovalStep step : steps) {
                step.setTemplate(existing);
                validateStepTimeout(step.getTimeoutHours());
                existing.getSteps().add(step);
            }
        }

        return approvalTemplateRepository.save(existing);
    }

    /**
     * 启用模板（启用后自动锁定）。
     *
     * @param id 模板 ID
     * @return 启用后的模板
     */
    public ApprovalTemplate enableTemplate(UUID id) {
        ApprovalTemplate template = approvalTemplateRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ApiCodes.APPROVAL_TEMPLATE_NOT_FOUND,
                        "审批模板不存在: " + id));

        template.validate();
        template.enable();
        return approvalTemplateRepository.save(template);
    }

    /**
     * 禁用模板（不解锁）。
     *
     * @param id 模板 ID
     * @return 禁用后的模板
     */
    public ApprovalTemplate disableTemplate(UUID id) {
        ApprovalTemplate template = approvalTemplateRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ApiCodes.APPROVAL_TEMPLATE_NOT_FOUND,
                        "审批模板不存在: " + id));

        template.disable();
        return approvalTemplateRepository.save(template);
    }

    /**
     * 删除模板（仅未锁定模板可删除）。
     *
     * @param id 模板 ID
     */
    public void deleteTemplate(UUID id) {
        ApprovalTemplate template = approvalTemplateRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ApiCodes.APPROVAL_TEMPLATE_NOT_FOUND,
                        "审批模板不存在: " + id));

        if (Boolean.TRUE.equals(template.getLocked())) {
            throw new BusinessException(ApiCodes.APPROVAL_TEMPLATE_LOCKED,
                    "审批模板已锁定，无法删除: " + id);
        }

        approvalTemplateRepository.delete(template);
    }

    @Transactional(readOnly = true)
    public ApprovalTemplate getTemplate(UUID id) {
        return approvalTemplateRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ApiCodes.APPROVAL_TEMPLATE_NOT_FOUND,
                        "审批模板不存在: " + id));
    }

    @Transactional(readOnly = true)
    public List<ApprovalTemplate> listAllTemplates() {
        return approvalTemplateRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ApprovalTemplate> listEnabledTemplates() {
        return approvalTemplateRepository.findByEnabledTrue();
    }

    @Transactional(readOnly = true)
    public List<ApprovalStep> getTemplateSteps(UUID templateId) {
        return approvalStepRepository.findByTemplateIdOrderByStepNumberAsc(templateId);
    }

    // =========================================================================
    // 内部辅助方法
    // =========================================================================

    private void validateSteps(List<ApprovalStep> steps, int expectedCount) {
        if (steps == null || steps.size() != expectedCount) {
            throw new BusinessException(ApiCodes.APPROVAL_INVALID_TEMPLATE,
                    "步骤数与模板定义不匹配: 期望 " + expectedCount + ", 实际 " +
                            (steps == null ? 0 : steps.size()));
        }
        for (int i = 0; i < steps.size(); i++) {
            ApprovalStep step = steps.get(i);
            if (step.getStepNumber() == null || step.getStepNumber() != i + 1) {
                throw new BusinessException(ApiCodes.APPROVAL_INVALID_TEMPLATE,
                        "步骤序号不连续: 期望 " + (i + 1) + ", 实际 " + step.getStepNumber());
            }
            if (step.getAssigneeRule() == null) {
                throw new BusinessException(ApiCodes.APPROVAL_INVALID_TEMPLATE,
                        "步骤 " + step.getStepNumber() + " 未设置分配规则");
            }
            if (step.getAssigneeValue() == null || step.getAssigneeValue().isBlank()) {
                throw new BusinessException(ApiCodes.APPROVAL_INVALID_TEMPLATE,
                        "步骤 " + step.getStepNumber() + " 未设置分配值");
            }
        }
    }

    private void validateTimeout(Integer timeoutHours) {
        if (timeoutHours == null) {
            return;
        }
        if (timeoutHours <= 0 || timeoutHours > MAX_TIMEOUT_HOURS_7D) {
            throw new BusinessException(ApiCodes.APPROVAL_TIMEOUT_NOT_CONFIGURED,
                    "超时时间必须在 1-" + MAX_TIMEOUT_HOURS_7D + " 小时之间");
        }
    }

    private void validateStepTimeout(Integer timeoutHours) {
        if (timeoutHours == null) {
            return;
        }
        boolean valid = false;
        for (int validHours : VALID_TIMEOUT_HOURS) {
            if (validHours == timeoutHours) {
                valid = true;
                break;
            }
        }
        if (!valid) {
            throw new BusinessException(ApiCodes.APPROVAL_TIMEOUT_NOT_CONFIGURED,
                    "步骤超时时间必须是 24h/72h/7d 之一，实际: " + timeoutHours + "h");
        }
    }
}
