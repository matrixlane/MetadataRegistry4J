package io.github.matrixlane.metadataregistry.web.service.database;

/**
 * 一张应用表的定位信息:发现时记录其所属 schema,后续 DELETE / ALTER TABLE
 * 等原生语句按发现时的 schema 限定,而不是再按表名猜测(避免硬编码清单漂移
 * 导致按错误 schema 限定,如 ext 表被限定到 mdr)。
 *
 * @param schema 所属 schema;SQLite 等无 schema 概念时为 null
 * @param name   表名(未限定)
 */
public record TableRef(String schema, String name) {
}
