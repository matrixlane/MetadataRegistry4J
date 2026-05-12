package io.github.matrixlane.metadataregistry.web.dto.classification;

/**
 * 分类导入/导出的一行数据表示（扁平表格式）。
 * <p>导入时：scheme_* 列用于指定或创建分类方案；node_* 列定义节点；parent_code 为空表示根节点。</p>
 */
public record ClassificationImportRow(
        String schemeName,
        String schemeDescription,
        String schemeVersion,
        String schemeStatus,
        String nodeCode,
        String nodeName,
        String nodeDescription,
        String nodeDefinition,
        String parentCode,
        Integer sortOrder
) {
}
