package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Individual;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.SubmissionRecord;
import io.github.matrixlane.metadataregistry.web.dto.common.SubmissionRecordSummary;

import java.util.List;
import java.util.UUID;

/**
 * SubmissionRecord → SubmissionRecordSummary 转换（align-submission-record-to-iso）。
 *
 * <p>Contact 名称优先取所关联 Individual 的 {@code name}（Sign.value），否则 Contact.id；
 * Organization 名称取 {@code name}（{@code List<Sign>}）第一条。</p>
 *
 * <p><b>registeredItemCount 由调用方提供</b>（2026-10-09）：原实现走
 * {@code sr.getRegisteredItems()}，那会把关联的每个 {@code RegisteredItem} 实体物化出来，
 * 而 RegisteredItem 是 Item 子类的 JOINED 继承根——一条提交记录关联上万条目时，
 * 每个条目的响应都要把上万实体全部加载（叠加 buildItemMeta 的逐条调用即数十秒级超时）。
 * 现改为由调用方按 {@code registered_item_submission_record} 做窄 COUNT 后传入，
 * 完全不触碰受管项侧的继承映射，也不内联上万条 ID。</p>
 */
final class SubmissionRecordMapper {

    private SubmissionRecordMapper() {
    }

    static SubmissionRecordSummary toSummary(SubmissionRecord sr, long registeredItemCount) {
        return new SubmissionRecordSummary(
                sr.getId(),
                contactLabel(sr.getContact()),
                organizationLabel(sr.getOrganization()),
                registeredItemCount);
    }

    private static String contactLabel(Contact contact) {
        if (contact == null) {
            return null;
        }
        Individual individual = contact.getIndividual();
        if (individual != null && individual.getName() != null) {
            String name = individual.getName().getValue();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return contact.getId() == null ? null : contact.getId().toString();
    }

    private static String organizationLabel(Organization organization) {
        if (organization == null || organization.getName() == null || organization.getName().isEmpty()) {
            return null;
        }
        Sign first = organization.getName().get(0);
        return first == null ? null : first.getValue();
    }
}