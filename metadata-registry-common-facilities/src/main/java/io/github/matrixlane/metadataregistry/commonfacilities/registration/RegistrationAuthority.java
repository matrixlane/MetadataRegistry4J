package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.RegistrationAuthorityIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class RegistrationAuthority extends Organization {

    @Embedded
    private RegistrationAuthorityIdentifier registrationAuthorityIdentifier;

    @ManyToMany
    @JoinTable(
            name = "registration_authority_doc_language",
            joinColumns = @JoinColumn(name = "registration_authority_id"),
            inverseJoinColumns = @JoinColumn(name = "language_id")
    )
    private List<LanguageIdentification> documentationLanguageIdentifier = new ArrayList<>();

    @OneToMany(mappedBy = "registrationAuthority")
    private List<Registrar> registrars = new ArrayList<>();

    @OneToOne(mappedBy = "registrationAuthority")
    private Namespace namespace;

    /**
     * 主要注册机构标志（应用扩展，ISO/IEC 11179-3:2023 表 24 无此属性）：
     * 初始化向导创建的注册机构在后台被标记为 Primary（隐式标志，不进入任何
     * 响应 DTO 或前端界面）；个人版据此实施删除保护。存量已初始化库由启动
     * 调和回填（无任何 Primary 行时取 id 字典序最小的既有机构补标）。
     */
    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary = false;
}
