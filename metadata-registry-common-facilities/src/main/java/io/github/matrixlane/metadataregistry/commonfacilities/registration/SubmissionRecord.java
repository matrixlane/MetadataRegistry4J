package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class SubmissionRecord extends Entity {

    @ManyToOne
    /** ISO/IEC 11179-3:2023 表 27(Submission_Record):organization 1,必备。 */
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne
    /** 表 27:contact 1,必备。 */
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    @ManyToMany
    @JoinTable(
            name = "submission_record_registered_item",
            joinColumns = @JoinColumn(name = "submission_record_id"),
            inverseJoinColumns = @JoinColumn(name = "registered_item_id")
    )
    private List<RegisteredItem> registeredItems = new ArrayList<>();

    /**
     * SR 来源（应用扩展属性，align-submission-record-to-iso）：
     * <ul>
     *     <li>{@link SubmissionRecordSource#USER_SUBMISSION} — 工作空间提交产生</li>
     *     <li>{@link SubmissionRecordSource#SYSTEM_DRAFT} — 历史脏数据迁移一次性标注</li>
     * </ul>
     * 默认 USER_SUBMISSION；新代码路径一律显式传入。SYSTEM_DRAFT 实例不再产生。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 32)
    private SubmissionRecordSource source = SubmissionRecordSource.USER_SUBMISSION;
}
