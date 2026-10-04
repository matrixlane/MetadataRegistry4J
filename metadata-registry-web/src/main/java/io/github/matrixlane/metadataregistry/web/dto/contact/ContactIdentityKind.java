package io.github.matrixlane.metadataregistry.web.dto.contact;

/**
 * "我的"联系人身份类型：联系人（Contact）或注册人员（Registrar，ISO/IEC 11179-3 中
 * Registrar 是 Contact 的特化，身份由实体类型确定）。仅在 createMyContact 创建时生效，
 * kind 缺省按 CONTACT 处理；编辑时类型不可变更（类型即身份）。
 */
public enum ContactIdentityKind {
    CONTACT,
    REGISTRAR
}
