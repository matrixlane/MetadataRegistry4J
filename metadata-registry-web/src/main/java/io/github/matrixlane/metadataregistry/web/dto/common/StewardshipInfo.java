package io.github.matrixlane.metadataregistry.web.dto.common;

import java.util.UUID;

/**
 * 主管信息（Stewardship_Record 内容）：
 * 条目保存时设置 Stewardship_Record 的联系人与所属组织。显式给出的联系人 MUST 属于当前账号
 * 绑定的 Contact；organizationId 缺省时取联系人所属组织。NULL 表示不修改既有主管记录。
 */
public record StewardshipInfo(UUID contactId, UUID organizationId) {
}
