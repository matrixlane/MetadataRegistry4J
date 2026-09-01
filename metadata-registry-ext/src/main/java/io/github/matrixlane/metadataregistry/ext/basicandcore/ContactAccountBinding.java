package io.github.matrixlane.metadataregistry.ext.basicandcore;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Contact 与登录账号(Account)绑定的卫星实体(扩展,非 ISO/IEC 11179 标准模型)。
 *
 * <p>标准实体 {@code Contact}(ISO/IEC 11179-3:2023, 6.3.5)只持有
 * Organization/Individual/Role 三个标准关联;Contact↔Account 的登录账号绑定
 * 为注册表软件自身扩展,剥离至本卫星实体(表 contact_account_binding,
 * 按 contact_id 一一对应)。一个 Account 可绑定多个 Contact(多机构/多角色),
 * 故 contact_id 唯一、account_id 不唯一。
 *
 * <p>有意不建立到 Contact/Account 的 JPA 关联:避免卫星加载反向触发
 * 标准实体对象图,读写一律经值键。阶段 3 起归入 EXT schema。
 */
@jakarta.persistence.Entity
@Table(name = "contact_account_binding")
@Getter
@Setter
@NoArgsConstructor
public class ContactAccountBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "contact_id", nullable = false, unique = true)
    private UUID contactId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    public ContactAccountBinding(UUID contactId, UUID accountId) {
        this.contactId = contactId;
        this.accountId = accountId;
    }
}
