package io.github.matrixlane.metadataregistry.web.inference.provider;

/**
 * 提供方可用性探测结果。
 *
 * @param id          提供方标识（cli）
 * @param displayName 展示名
 * @param available   当前是否可用
 * @param detail      不可用原因或探测详情（如版本输出片段）
 */
public record ProviderStatus(String id, String displayName, boolean available, String detail) {
}
