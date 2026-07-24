package io.github.matrixlane.metadataregistry.web.federation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 联邦节点：对端注册表实例登记。
 *
 * <p>baseUrl 可空（离线节点仅文件通道）。authTokenEncrypted 为预共享令牌的
 * AES-GCM 密文（密钥派生自 app.jwt.secret），API 响应中永不回传。
 */
@Entity
@Table(name = "registry_node")
@Getter
@Setter
@NoArgsConstructor
public class RegistryNode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "base_url", length = 512)
    private String baseUrl;

    /**
     * 对端注册机构标识（RAI 的 organizationIdentifier），全局逻辑身份的组成之一。
     */
    @Column(name = "rai", nullable = false, length = 64)
    private String rai;

    @Column(name = "auth_token_encrypted", length = 1024)
    private String authTokenEncrypted;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
