package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Individual;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.SubmissionRecord;
import io.github.matrixlane.metadataregistry.web.dto.common.SubmissionRecordSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SubmissionRecord → SubmissionRecordSummary 转换（align-submission-record-to-iso）。
 *
 * <p>不触碰懒加载关联实体；registeredItems 通过实体已加载的 List 取 ID。
 * Contact 名称优先取所关联 Individual 的 {@code name}（Sign.value），否则 Contact.id；
 * Organization 名称取 {@code name}（{@code List<Sign>}）第一条。</p>
 */
final class SubmissionRecordMapper {

    private SubmissionRecordMapper() {
    }

    static SubmissionRecordSummary toSummary(SubmissionRecord sr) {
        String contactLabel = contactLabel(sr.getContact());
        String organizationLabel = organizationLabel(sr.getOrganization());
        List<UUID> registeredItemIds = sr.getRegisteredItems() == null
                ? List.of()
                : sr.getRegisteredItems().stream()
                .map(ri -> ri == null || ri.getItem() == null ? null : ri.getItem().getId())
                .filter(java.util.Objects::nonNull)
                .toList();
        int count = registeredItemIds.size();
        return new SubmissionRecordSummary(
                sr.getId(),
                contactLabel,
                organizationLabel,
                count,
                new ArrayList<>(registeredItemIds));
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