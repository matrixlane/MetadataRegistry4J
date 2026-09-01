package io.github.matrixlane.metadataregistry.web.service.datadictionary;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.dto.export.datadictionary.DataDictionaryItemExport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DataDictionaryPdfExportService} 单元测试
 */
class DataDictionaryPdfExportServiceTest {

    private DataDictionaryPdfExportService exportService;
    private DataDictionaryItemExport sampleItem;
    private List<DataDictionaryItemExport> sampleItems;

    @BeforeEach
    void setUp() {
        exportService = new DataDictionaryPdfExportService();

        sampleItem = createItem("DE001", "1.0", "患者姓名", "患者姓名的定义",
                RegistrationStatus.STANDARD, "字符串", "姓名值域",
                List.of());

        sampleItems = List.of(
                sampleItem,
                createItem("DE002", "1.0", "患者性别", "患者性别的定义",
                        RegistrationStatus.QUALIFIED, "字符串", "性别值域",
                        List.of(new DataDictionaryItemExport.PermissibleValueExport("1", "男", "男性"),
                                new DataDictionaryItemExport.PermissibleValueExport("2", "女", "女性"))),
                createItem("DE003", "1.0", "出生日期", "出生日期的定义",
                        RegistrationStatus.CANDIDATE, "日期", "日期值域", List.of())
        );
    }

    // =========================================================================
    // 1. 单数据元 PDF 导出
    // =========================================================================

    @Test
    @DisplayName("单数据元 PDF - 应生成非空文件")
    void exportSingleItemPdf_shouldProduceNonEmptyFile() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportSingleItemPdf(sampleItem, baos);

        byte[] bytes = baos.toByteArray();
        assertThat(bytes).isNotEmpty();
        assertThat(bytes.length).isGreaterThan(100);

        // PDF 文件以 %PDF 开头
        assertThat(new String(bytes, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1))
                .startsWith("%PDF");
    }

    @Test
    @DisplayName("单数据元 PDF - 含可允许值时应生成有效文件")
    void exportSingleItemPdf_withPermissibleValues_shouldProduceValidFile() throws IOException {
        DataDictionaryItemExport itemWithPvs = createItem("DE004", "1.0", "性别代码", "性别代码定义",
                RegistrationStatus.STANDARD, "字符串", "性别代码值域",
                List.of(new DataDictionaryItemExport.PermissibleValueExport("1", "男", "男性"),
                        new DataDictionaryItemExport.PermissibleValueExport("2", "女", "女性"),
                        new DataDictionaryItemExport.PermissibleValueExport("9", "未知", "未知性别")));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportSingleItemPdf(itemWithPvs, baos);

        byte[] bytes = baos.toByteArray();
        assertThat(bytes).isNotEmpty();
        assertThat(new String(bytes, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1))
                .startsWith("%PDF");
    }

    // =========================================================================
    // 2. 完整报告 PDF 导出
    // =========================================================================

    @Test
    @DisplayName("完整报告 PDF - 应生成非空文件")
    void exportDataDictionaryReport_shouldProduceNonEmptyFile() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportDataDictionaryReport(sampleItems, "数据字典报告", baos);

        byte[] bytes = baos.toByteArray();
        assertThat(bytes).isNotEmpty();
        assertThat(bytes.length).isGreaterThan(200);

        assertThat(new String(bytes, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1))
                .startsWith("%PDF");
    }

    @Test
    @DisplayName("完整报告 PDF - 空列表也应生成有效文件")
    void exportDataDictionaryReport_emptyList_shouldProduceValidFile() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportDataDictionaryReport(List.of(), "空报告", baos);

        byte[] bytes = baos.toByteArray();
        assertThat(bytes).isNotEmpty();
        assertThat(new String(bytes, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1))
                .startsWith("%PDF");
    }

    @Test
    @DisplayName("完整报告 PDF - 大数据量应生成有效文件")
    void exportDataDictionaryReport_largeList_shouldProduceValidFile() throws IOException {
        List<DataDictionaryItemExport> largeList = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            largeList.add(createItem("DE" + i, "1.0", "测试项" + i, "测试定义" + i,
                    RegistrationStatus.STANDARD, "字符串", "测试值域", List.of()));
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportDataDictionaryReport(largeList, "大数据字典报告", baos);

        byte[] bytes = baos.toByteArray();
        assertThat(bytes).isNotEmpty();
        assertThat(new String(bytes, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1))
                .startsWith("%PDF");
    }

    // =========================================================================
    // 3. Null 安全验证
    // =========================================================================

    @Test
    @DisplayName("单数据元 PDF - null 字段应正常处理")
    void exportSingleItemPdf_nullFields_shouldHandleGracefully() throws IOException {
        DataDictionaryItemExport itemWithNulls = new DataDictionaryItemExport(
                UUID.randomUUID(), null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null
        );

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportSingleItemPdf(itemWithNulls, baos);

        byte[] bytes = baos.toByteArray();
        assertThat(bytes).isNotEmpty();
        assertThat(new String(bytes, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1))
                .startsWith("%PDF");
    }

    // =========================================================================
    // 辅助方法
    // =========================================================================

    private DataDictionaryItemExport createItem(String identifier, String version, String label,
                                                 String definition, RegistrationStatus status,
                                                 String dataTypeLabel, String valueDomainLabel,
                                                 List<DataDictionaryItemExport.PermissibleValueExport> pvs) {
        return new DataDictionaryItemExport(
                UUID.randomUUID(),
                "DataElement",
                label,
                definition,
                identifier,
                version,
                status,
                null,
                null,
                UUID.randomUUID(),
                valueDomainLabel,
                dataTypeLabel,
                null,
                null,
                null,
                List.of(label),
                List.of(definition),
                pvs,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 6, 1, 0, 0),
                "初始创建",
                null,
                "system"
        );
    }
}
