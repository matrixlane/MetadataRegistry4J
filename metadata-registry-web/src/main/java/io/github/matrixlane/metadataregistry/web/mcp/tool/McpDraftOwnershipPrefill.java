package io.github.matrixlane.metadataregistry.web.mcp.tool;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.web.service.ActiveRegistrarService;
import io.github.matrixlane.metadataregistry.web.service.ContactAccountService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * MCP 起草归属预填（add-mcp-server-support）：业务面 UI 创建时由 ItemFormDialog
 * 在"提交信息区"预填当前账号的默认联系人，使新建条目立即归属当前身份；MCP 的
 * draft-item 无此前端预填，若不在工具层补等价语义，产出条目将无任何归属
 * （提交/请求候选时被"该管理项不属于当前用户"拒绝——e2e 实证）。
 *
 * <p>预填规则：payload.registration.stewardship 未携带有效 contactId 时，
 * 取当前账号的默认 Registrar 身份（退化为首个绑定联系人）写入 stewardship
 * 块；组织缺省由既有 applyStewardshipRecord 回退联系人所属组织。系统/无认证
 * 路径（accountId 为 null）不预填，保持既有行为。
 */
@Component
public class McpDraftOwnershipPrefill {

    private final ActiveRegistrarService activeRegistrarService;
    private final ContactAccountService contactAccountService;

    public McpDraftOwnershipPrefill(ActiveRegistrarService activeRegistrarService,
                                    ContactAccountService contactAccountService) {
        this.activeRegistrarService = activeRegistrarService;
        this.contactAccountService = contactAccountService;
    }

    /** 若缺 stewardship 则预填默认联系人；返回新 payload（不改传入实例）。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> prefillStewardship(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object registration = payload.get("registration");
        Map<String, Object> registrationMap = registration instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        Object stewardship = registrationMap.get("stewardship");
        if (stewardship instanceof Map<?, ?> existing
                && ((Map<String, Object>) existing).get("contactId") != null) {
            return payload; // 调用方显式指定：尊重之（applyStewardshipRecord 会做归属校验）
        }
        UUID accountId = activeRegistrarService.currentAccountIdOrNull();
        if (accountId == null) {
            return payload; // 系统路径不预填
        }
        UUID contactId = resolveDefaultContactId(accountId);
        if (contactId == null) {
            return payload; // 账号无任何联系人身份：不预填（后续归属校验如实报错）
        }
        Map<String, Object> stewardshipMap = new LinkedHashMap<>();
        stewardshipMap.put("contactId", contactId.toString());
        registrationMap.put("stewardship", stewardshipMap);
        Map<String, Object> copy = new LinkedHashMap<>(payload);
        copy.put("registration", registrationMap);
        return copy;
    }

    private UUID resolveDefaultContactId(UUID accountId) {
        Optional<Registrar> defaultRegistrar = contactAccountService.defaultRegistrarOf(accountId);
        if (defaultRegistrar.isPresent()) {
            return defaultRegistrar.get().getId();
        }
        List<UUID> bound = contactAccountService.boundContactIdsOfAccount(accountId);
        return bound.isEmpty() ? null : bound.get(0);
    }
}
