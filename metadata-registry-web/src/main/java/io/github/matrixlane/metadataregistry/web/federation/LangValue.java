package io.github.matrixlane.metadataregistry.web.federation;

/**
 * 语言标识 + 文本值（designation/definition 快照用）。
 */
public record LangValue(String languageIdentifier, String value) {}
