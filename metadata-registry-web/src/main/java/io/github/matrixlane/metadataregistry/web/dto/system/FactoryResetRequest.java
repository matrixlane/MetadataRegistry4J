package io.github.matrixlane.metadataregistry.web.dto.system;

/**
 * 恢复出厂设置请求。
 *
 * <p>团队版（TEAM）：{@code password} 必填，为当前管理员密码；
 * 个人版（PERSONAL）：无密码概念，{@code confirmationPhrase} 必填，须与固定
 * 确认短语"恢复出厂设置"精确匹配。两者均由服务端按部署版本强制校验，
 * 故此处不做 bean-validation 必填约束。</p>
 */
public record FactoryResetRequest(
    String password,
    String confirmationPhrase
) {}
