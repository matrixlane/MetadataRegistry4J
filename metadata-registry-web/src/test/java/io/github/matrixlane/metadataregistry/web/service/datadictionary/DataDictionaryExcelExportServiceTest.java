package io.github.matrixlane.metadataregistry.web.service.datadictionary;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.dto.export.datadictionary.DataDictionaryItemExport;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DataDictionaryExcelExportService} 单元测试
 */
class DataDictionaryExcelExportServiceTest {

    private DataDictionaryExcelExportService exportService;
    private List<DataDictionaryItemExport> sampleItems;

    @BeforeEach
    void setUp() {
        exportService = new DataDictionaryExcelExportService();

        sampleItems = List.of(
                createItem("DE001", "1.0", "患者姓名", "患者姓名的定义",
                        RegistrationStatus.STANDARD, "字符串", "姓名值域",
                        List.of(new DataDictionaryItemExport.PermissibleValueExport("1", "男", "男性"),
                                new DataDictionaryItemExport.PermissibleValueExport("2", "女", "女性"))),
                createItem("DE002", "1.0", "患者性别", "患者性别的定义",
                        RegistrationStatus.QUALIFIED, "字符串", "性别值域", List.of()),
                createItem("DE003", "1.0", "出生日期", "出生日期的定义",
                        RegistrationStatus.CANDIDATE, "日期", "日期值域", List.of())
        );
    }

    // =========================================================================
    // 1. 基本导出验证
    // =========================================================================

    @Test
    @DisplayName("导出 Excel - 应包含数据字典工作表")
    void exportToExcel_shouldContainDataDictionarySheet() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportToExcel(sampleItems, baos, false, false);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            assertThat(workbook.getSheetName(0)).isEqualTo("数据字典");
        }
    }

    @Test
    @DisplayName("导出 Excel - 含值域详情时应包含两个工作表")
    void exportToExcel_withValueDomainDetails_shouldContainTwoSheets() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportToExcel(sampleItems, baos, true, false);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            assertThat(workbook.getSheetName(0)).isEqualTo("数据字典");
            assertThat(workbook.getSheetName(1)).isEqualTo("值域明细");
        }
    }

    @Test
    @DisplayName("导出 Excel - 数据字典表应包含表头和数据行")
    void exportToExcel_dataSheet_shouldContainHeaderAndData() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportToExcel(sampleItems, baos, false, false);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            // 标题 + 元信息 + 空行 + 表头 + 3条数据
            assertThat(sheet.getPhysicalNumberOfRows()).isGreaterThanOrEqualTo(5);

            // 表头应在第3行（0-based index）
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("类型");
            assertThat(sheet.getRow(3).getCell(4).getStringCellValue()).isEqualTo("名称");
        }
    }

    @Test
    @DisplayName("导出 Excel - 数据行内容正确")
    void exportToExcel_dataRows_shouldContainCorrectValues() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportToExcel(sampleItems, baos, false, false);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            // 数据从第4行开始
            assertThat(sheet.getRow(4).getCell(2).getStringCellValue()).isEqualTo("DE001");
            assertThat(sheet.getRow(4).getCell(4).getStringCellValue()).isEqualTo("患者姓名");
            assertThat(sheet.getRow(5).getCell(4).getStringCellValue()).isEqualTo("患者性别");
            assertThat(sheet.getRow(6).getCell(4).getStringCellValue()).isEqualTo("出生日期");
        }
    }

    @Test
    @DisplayName("导出 Excel - 含注册信息时应包含注册状态列")
    void exportToExcel_withRegistrationInfo_shouldContainStatusColumn() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportToExcel(sampleItems, baos, false, true);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            // 表头行
            Row headerRow = sheet.getRow(3);
            boolean hasStatusColumn = false;
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                if ("注册状态".equals(headerRow.getCell(i).getStringCellValue())) {
                    hasStatusColumn = true;
                    break;
                }
            }
            assertThat(hasStatusColumn).isTrue();
        }
    }

    // =========================================================================
    // 2. 值域明细工作表验证
    // =========================================================================

    @Test
    @DisplayName("导出 Excel - 值域明细表应包含可允许值")
    void exportToExcel_valueDomainSheet_shouldContainPermissibleValues() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportToExcel(sampleItems, baos, true, false);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
            Sheet sheet = workbook.getSheet("值域明细");
            assertThat(sheet).isNotNull();
            // 表头 + 2条可允许值
            assertThat(sheet.getPhysicalNumberOfRows()).isGreaterThanOrEqualTo(3);
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("1");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("男");
            assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).isEqualTo("2");
            assertThat(sheet.getRow(2).getCell(4).getStringCellValue()).isEqualTo("女");
        }
    }

    // =========================================================================
    // 3. 空数据验证
    // =========================================================================

    @Test
    @DisplayName("导出 Excel - 空列表应生成有效文件")
    void exportToExcel_emptyList_shouldProduceValidFile() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportToExcel(List.of(), baos, false, false);

        assertThat(baos.size()).isGreaterThan(0);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
        }
    }

    // =========================================================================
    // 4. 流式导出验证
    // =========================================================================

    @Test
    @DisplayName("流式导出 Excel - 应生成有效文件")
    void exportToExcelStreaming_shouldProduceValidFile() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportToExcelStreaming(sampleItems, baos, false, false);

        assertThat(baos.size()).isGreaterThan(0);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
            assertThat(workbook.getNumberOfSheets()).isGreaterThanOrEqualTo(1);
            assertThat(workbook.getSheetAt(0).getPhysicalNumberOfRows()).isGreaterThan(0);
        }
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
