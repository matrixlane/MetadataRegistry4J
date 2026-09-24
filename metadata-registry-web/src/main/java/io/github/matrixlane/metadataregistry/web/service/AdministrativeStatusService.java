package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministrativeStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 管理状态（administrative status）写入服务。
 *
 * <p>管理状态变更只改写当前 {@code Registration_State} 行的
 * {@code administrative_status} 属性（0..1），不新建注册状态行、不动
 * {@code previous_state} 链。本服务集中所有管理状态落点与清空逻辑，供
 * 编辑、提交、迁移、批准/拒绝、回退等流程调用。</p>
 */
@Slf4j
@Service
public class AdministrativeStatusService {

    /**
     * 提交后的管理状态落点：INCOMPLETE 进入 INITIAL，其余注册状态清空（null）。
     *
     * <p>HISTORICAL / APPLICATION 等文档态入口同样返回 null。</p>
     */
    public AdministrativeStatus landingOf(RegistrationStatus registrationStatus) {
        if (registrationStatus == RegistrationStatus.INCOMPLETE) {
            return AdministrativeStatus.INITIAL;
        }
        return null;
    }

    /**
     * 将管理项标记为草稿（DRAFT）。
     *
     * <p>用于新建保存与编辑保存后的统一钩子。无当前 RegistrationState 时不动并 warn。</p>
     */
    public void markDraft(AdministeredItem item) {
        if (item == null || item.getRegistrationState() == null) {
            log.warn("无法标记草稿：管理项或当前注册状态行缺失");
            return;
        }
        item.getRegistrationState().setAdministrativeStatus(AdministrativeStatus.DRAFT);
    }

    /**
     * 清空管理状态（置 null）。
     *
     * <p>用于注册状态迁移、批准生效、回退等场景。</p>
     */
    public void clear(AdministeredItem item) {
        if (item == null || item.getRegistrationState() == null) {
            return;
        }
        item.getRegistrationState().setAdministrativeStatus(null);
    }

    /**
     * 按当前注册状态应用提交后的管理状态落点。
     *
     * <p>INCOMPLETE → INITIAL，其余 → null。工作空间提交后调用。</p>
     */
    public void applyLanding(AdministeredItem item) {
        if (item == null || item.getRegistrationState() == null) {
            log.warn("无法应用管理状态落点：管理项或当前注册状态行缺失");
            return;
        }
        AdministrativeStatus landed = landingOf(item.getRegistrationState().getRegistrationStatus());
        item.getRegistrationState().setAdministrativeStatus(landed);
    }

    /**
     * 将管理项标记为已驳回（REJECTED）。
     *
     * <p>用于 Registrar 驳回进行中的 RECORDED→QUALIFIED 申请。</p>
     */
    public void setRejected(AdministeredItem item) {
        if (item == null || item.getRegistrationState() == null) {
            log.warn("无法标记驳回：管理项或当前注册状态行缺失");
            return;
        }
        item.getRegistrationState().setAdministrativeStatus(AdministrativeStatus.REJECTED);
    }

    /**
     * 将管理项标记为申请合格中（PROVISIONALLY_QUALIFIED）。
     *
     * <p>用于主管发起 RECORDED→QUALIFIED 审核流。</p>
     */
    public void setProvisionallyQualified(AdministeredItem item) {
        if (item == null || item.getRegistrationState() == null) {
            log.warn("无法标记申请合格：管理项或当前注册状态行缺失");
            return;
        }
        item.getRegistrationState().setAdministrativeStatus(AdministrativeStatus.PROVISIONALLY_QUALIFIED);
    }
}
