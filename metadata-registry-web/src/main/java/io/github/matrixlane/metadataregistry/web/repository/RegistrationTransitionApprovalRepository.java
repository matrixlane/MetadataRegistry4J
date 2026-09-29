package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationTransitionApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RegistrationTransitionApprovalRepository
        extends JpaRepository<RegistrationTransitionApproval, UUID> {

    List<RegistrationTransitionApproval> findByAdministeredItemId(UUID administeredItemId);

    List<RegistrationTransitionApproval> findByAdministeredItemIdAndFromStatusAndToStatus(
            UUID administeredItemId, RegistrationStatus fromStatus, RegistrationStatus toStatus);

    boolean existsByAdministeredItemIdAndFromStatusAndToStatusAndRegistrarId(
            UUID administeredItemId, RegistrationStatus fromStatus, RegistrationStatus toStatus,
            UUID registrarId);

    /** 注册人员身份删除前的审批记录引用检查（外键非空，删除必失败）。 */
    boolean existsByRegistrarId(UUID registrarId);

    long countByAdministeredItemIdAndFromStatusAndToStatus(
            UUID administeredItemId, RegistrationStatus fromStatus, RegistrationStatus toStatus);

    /** 状态变化后清除该项全部未生效审批（防陈旧）。 */
    void deleteByAdministeredItemId(UUID administeredItemId);

    /** 清除指定迁移的全部未生效审批（用于拒绝等不改变注册状态的场景）。 */
    void deleteByAdministeredItemIdAndFromStatusAndToStatus(
            UUID administeredItemId, RegistrationStatus fromStatus, RegistrationStatus toStatus);
}
