package io.github.matrixlane.metadataregistry.web.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "namespace_permission")
@Getter
@Setter
@NoArgsConstructor
public class NamespacePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "namespace_id")
    private UUID namespaceId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_type", nullable = false, length = 20)
    private NamespacePermissionType permissionType;

    public NamespacePermission(UUID namespaceId, UUID accountId, UUID organizationId, NamespacePermissionType permissionType) {
        this.namespaceId = namespaceId;
        this.accountId = accountId;
        this.organizationId = organizationId;
        this.permissionType = permissionType;
    }
}

