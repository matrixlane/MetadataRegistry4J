package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalStep;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalStepAssigneeRule;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalTemplate;
import io.github.matrixlane.metadataregistry.web.entity.RejectStrategy;
import io.github.matrixlane.metadataregistry.web.repository.ApprovalStepRepository;
import io.github.matrixlane.metadataregistry.web.repository.ApprovalTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApprovalTemplateService} 单元测试。
 *
 * <p>覆盖目标：</p>
 * <ul>
 *   <li>创建模板（正常/名称重复/步骤数不匹配/超时时间不合法）</li>
 *   <li>更新模板（正常/已锁定/名称重复）</li>
 *   <li>启用/禁用模板</li>
 *   <li>删除模板（正常/已锁定）</li>
 *   <li>查询功能</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ApprovalTemplateServiceTest {

    @Mock
    private ApprovalTemplateRepository approvalTemplateRepository;

    @Mock
    private ApprovalStepRepository approvalStepRepository;

    @InjectMocks
    private ApprovalTemplateService approvalTemplateService;

    private UUID templateId;
    private ApprovalTemplate template;
    private List<ApprovalStep> steps;

    @BeforeEach
    void setUp() {
        templateId = UUID.randomUUID();
        template = createTemplate();
        steps = createSteps(template);
    }

    // =========================================================================
    // 1. 创建模板测试
    // =========================================================================

    @Test
    @DisplayName("创建模板：正常流程，2级审批")
    void createTemplate_shouldCreateWithSteps() {
        when(approvalTemplateRepository.existsByName("2级审批模板")).thenReturn(false);
        when(approvalTemplateRepository.save(any())).thenAnswer(inv -> {
            ApprovalTemplate t = inv.getArgument(0);
            t.setId(templateId);
            return t;
        });
        when(approvalStepRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApprovalTemplate result = approvalTemplateService.createTemplate(template, steps);

        assertThat(result.getId()).isEqualTo(templateId);
        assertThat(result.getName()).isEqualTo("2级审批模板");
        assertThat(result.getStepCount()).isEqualTo(2);
        assertThat(result.getEnabled()).isFalse();
        assertThat(result.getLocked()).isFalse();
    }

    @Test
    @DisplayName("创建模板：名称重复应抛出异常")
    void createTemplate_duplicateName_shouldThrow() {
        when(approvalTemplateRepository.existsByName("2级审批模板")).thenReturn(true);

        assertThatThrownBy(() -> approvalTemplateService.createTemplate(template, steps))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_TEMPLATE_NAME_DUPLICATE);
                });
    }

    @Test
    @DisplayName("创建模板：步骤数不匹配应抛出异常")
    void createTemplate_stepCountMismatch_shouldThrow() {
        when(approvalTemplateRepository.existsByName("2级审批模板")).thenReturn(false);
        List<ApprovalStep> wrongSteps = List.of(steps.get(0)); // 只有1步

        assertThatThrownBy(() -> approvalTemplateService.createTemplate(template, wrongSteps))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_INVALID_TEMPLATE);
                });
    }

    @Test
    @DisplayName("创建模板：步骤序号不连续应抛出异常")
    void createTemplate_stepNumberNotContinuous_shouldThrow() {
        when(approvalTemplateRepository.existsByName("2级审批模板")).thenReturn(false);
        steps.get(1).setStepNumber(3); // 跳过2

        assertThatThrownBy(() -> approvalTemplateService.createTemplate(template, steps))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_INVALID_TEMPLATE);
                });
    }

    @Test
    @DisplayName("创建模板：步骤未设置分配规则应抛出异常")
    void createTemplate_missingAssigneeRule_shouldThrow() {
        when(approvalTemplateRepository.existsByName("2级审批模板")).thenReturn(false);
        steps.get(0).setAssigneeRule(null);

        assertThatThrownBy(() -> approvalTemplateService.createTemplate(template, steps))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_INVALID_TEMPLATE);
                });
    }

    @Test
    @DisplayName("创建模板：超时时间不合法应抛出异常")
    void createTemplate_invalidTimeout_shouldThrow() {
        when(approvalTemplateRepository.existsByName("2级审批模板")).thenReturn(false);
        template.setDefaultTimeoutHours(999); // 超过7天

        assertThatThrownBy(() -> approvalTemplateService.createTemplate(template, steps))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_TIMEOUT_NOT_CONFIGURED);
                });
    }

    @Test
    @DisplayName("创建模板：步骤超时时间非法值应抛出异常")
    void createTemplate_invalidStepTimeout_shouldThrow() {
        when(approvalTemplateRepository.existsByName("2级审批模板")).thenReturn(false);
        steps.get(0).setTimeoutHours(48); // 不是24/72/168

        assertThatThrownBy(() -> approvalTemplateService.createTemplate(template, steps))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_TIMEOUT_NOT_CONFIGURED);
                });
    }

    // =========================================================================
    // 2. 更新模板测试
    // =========================================================================

    @Test
    @DisplayName("更新模板：正常流程")
    void updateTemplate_shouldUpdate() {
        ApprovalTemplate existing = createTemplate();
        existing.setId(templateId);
        existing.setLocked(false);

        when(approvalTemplateRepository.findById(templateId)).thenReturn(Optional.of(existing));
        when(approvalTemplateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApprovalTemplate update = createTemplate();
        update.setName("更新后的模板");

        ApprovalTemplate result = approvalTemplateService.updateTemplate(templateId, update, null);

        assertThat(result.getName()).isEqualTo("更新后的模板");
    }

    @Test
    @DisplayName("更新模板：已锁定应抛出异常")
    void updateTemplate_locked_shouldThrow() {
        ApprovalTemplate existing = createTemplate();
        existing.setId(templateId);
        existing.setLocked(true);

        when(approvalTemplateRepository.findById(templateId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> approvalTemplateService.updateTemplate(templateId, template, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_TEMPLATE_LOCKED);
                });
    }

    @Test
    @DisplayName("更新模板：不存在应抛出异常")
    void updateTemplate_notFound_shouldThrow() {
        when(approvalTemplateRepository.findById(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalTemplateService.updateTemplate(templateId, template, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_TEMPLATE_NOT_FOUND);
                });
    }

    // =========================================================================
    // 3. 启用/禁用模板测试
    // =========================================================================

    @Test
    @DisplayName("启用模板：正常流程，自动锁定")
    void enableTemplate_shouldEnableAndLock() {
        ApprovalTemplate existing = createTemplate();
        existing.setId(templateId);
        existing.setEnabled(false);
        existing.setLocked(false);
        existing.setSteps(new ArrayList<>(steps));

        when(approvalTemplateRepository.findById(templateId)).thenReturn(Optional.of(existing));
        when(approvalTemplateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApprovalTemplate result = approvalTemplateService.enableTemplate(templateId);

        assertThat(result.getEnabled()).isTrue();
        assertThat(result.getLocked()).isTrue();
    }

    @Test
    @DisplayName("启用模板：不存在应抛出异常")
    void enableTemplate_notFound_shouldThrow() {
        when(approvalTemplateRepository.findById(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalTemplateService.enableTemplate(templateId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_TEMPLATE_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("禁用模板：正常流程")
    void disableTemplate_shouldDisable() {
        ApprovalTemplate existing = createTemplate();
        existing.setId(templateId);
        existing.setEnabled(true);
        existing.setLocked(true);

        when(approvalTemplateRepository.findById(templateId)).thenReturn(Optional.of(existing));
        when(approvalTemplateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApprovalTemplate result = approvalTemplateService.disableTemplate(templateId);

        assertThat(result.getEnabled()).isFalse();
    }

    // =========================================================================
    // 4. 删除模板测试
    // =========================================================================

    @Test
    @DisplayName("删除模板：正常流程")
    void deleteTemplate_shouldDelete() {
        ApprovalTemplate existing = createTemplate();
        existing.setId(templateId);
        existing.setLocked(false);

        when(approvalTemplateRepository.findById(templateId)).thenReturn(Optional.of(existing));

        approvalTemplateService.deleteTemplate(templateId);

        verify(approvalTemplateRepository).delete(existing);
    }

    @Test
    @DisplayName("删除模板：已锁定应抛出异常")
    void deleteTemplate_locked_shouldThrow() {
        ApprovalTemplate existing = createTemplate();
        existing.setId(templateId);
        existing.setLocked(true);

        when(approvalTemplateRepository.findById(templateId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> approvalTemplateService.deleteTemplate(templateId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_TEMPLATE_LOCKED);
                });

        verify(approvalTemplateRepository, never()).delete(any());
    }

    // =========================================================================
    // 5. 查询测试
    // =========================================================================

    @Test
    @DisplayName("查询模板：正常返回")
    void getTemplate_shouldReturn() {
        when(approvalTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));

        ApprovalTemplate result = approvalTemplateService.getTemplate(templateId);

        assertThat(result.getId()).isEqualTo(templateId);
    }

    @Test
    @DisplayName("查询模板：不存在应抛出异常")
    void getTemplate_notFound_shouldThrow() {
        when(approvalTemplateRepository.findById(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalTemplateService.getTemplate(templateId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_TEMPLATE_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("查询全部模板：正常返回列表")
    void listAllTemplates_shouldReturnList() {
        when(approvalTemplateRepository.findAll()).thenReturn(List.of(template));

        List<ApprovalTemplate> result = approvalTemplateService.listAllTemplates();

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("查询已启用模板：正常返回列表")
    void listEnabledTemplates_shouldReturnEnabledList() {
        when(approvalTemplateRepository.findByEnabledTrue()).thenReturn(List.of(template));

        List<ApprovalTemplate> result = approvalTemplateService.listEnabledTemplates();

        assertThat(result).hasSize(1);
    }

    // =========================================================================
    // Helper 方法
    // =========================================================================

    private ApprovalTemplate createTemplate() {
        ApprovalTemplate t = new ApprovalTemplate();
        t.setId(templateId);
        t.setName("2级审批模板");
        t.setDescription("标准2级串行审批流程");
        t.setStepCount(2);
        t.setEnabled(false);
        t.setLocked(false);
        t.setDefaultTimeoutHours(72);
        t.setDefaultRejectStrategy(RejectStrategy.REJECT_ALL);
        return t;
    }

    private List<ApprovalStep> createSteps(ApprovalTemplate template) {
        ApprovalStep step1 = new ApprovalStep();
        step1.setId(UUID.randomUUID());
        step1.setTemplate(template);
        step1.setStepNumber(1);
        step1.setStepName("一级审批");
        step1.setAssigneeRule(ApprovalStepAssigneeRule.BY_USER);
        step1.setAssigneeValue("approver1");
        step1.setTimeoutHours(72);

        ApprovalStep step2 = new ApprovalStep();
        step2.setId(UUID.randomUUID());
        step2.setTemplate(template);
        step2.setStepNumber(2);
        step2.setStepName("二级审批");
        step2.setAssigneeRule(ApprovalStepAssigneeRule.BY_ROLE);
        step2.setAssigneeValue("ADMIN");
        step2.setTimeoutHours(168);

        return List.of(step1, step2);
    }
}
