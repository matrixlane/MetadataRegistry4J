package io.github.matrixlane.metadataregistry.web.service.compliancereport;

import io.github.matrixlane.metadataregistry.web.dto.compliancereport.ComplianceReport;
import io.github.matrixlane.metadataregistry.web.dto.compliancereport.DataSetReport;
import io.github.matrixlane.metadataregistry.web.dto.compliancereport.MappingEntry;
import io.github.matrixlane.metadataregistry.web.dto.compliancereport.MissingEntry;
import io.github.matrixlane.metadataregistry.web.dto.compliancereport.OverallScore;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ExcelExportService} 单元测试.
 */
class ExcelExportServiceTest {

    private ExcelExportService exportService;
    private ComplianceReport sampleReport;

    @BeforeEach
    void setUp() {
        exportService = new ExcelExportService();

        // 构建示例报告
        var mappings = List.of(
                new MappingEntry(1, "DE01.01.001", "患者姓名", "患者姓名定义",
                        "uuid-1234", "本地项[uuid-12...]", "已匹配", ""),
                new MappingEntry(2, "DE01.01.002", "患者性别", "患者性别定义",
                        "", "", "未匹配", "")
        );

        var missingEntries = List.of(
                new MissingEntry("DE01.01.002", "患者性别", "患者性别定义",
                        "HDSB01.01", "卫生服务数据集", "新建本地项")
        );

        var dataSetReport = new DataSetReport(
                "HDSB01.01", "卫生服务数据集",
                mappings, missingEntries, 0.5, 50.0);

        var dsScores = List.of(
                new OverallScore.DataSetScore("HDSB01.01", "卫生服务数据集", 0.5, 50.0));

        var overallScore = new OverallScore(50.0, dsScores,
                List.of("卫生服务数据集（HDSB01.01）匹配率为 50.00%，建议检查待确认项并完善映射关系。"));

        sampleReport = new ComplianceReport(
                "互联互通测评合规报告",
                LocalDateTime.of(2026, 5, 13, 8, 0),
                "testuser",
                List.of(dataSetReport),
                overallScore);
    }

    // =========================================================================
    // 1. Excel 文件结构验证
    // =========================================================================

    @Test
    @DisplayName("导出 Excel - 应包含 3 个工作表")
    void exportToExcel_shouldContainThreeSheets() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportToExcel(sampleReport, baos);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            assertThat(workbook.getSheetName(0)).isEqualTo("对照表");
            assertThat(workbook.getSheetName(1)).isEqualTo("缺失项清单");
            assertThat(workbook.getSheetName(2)).isEqualTo("评分摘要");
        }
    }

    // =========================================================================
    // 2. 对照表工作表验证
    // =========================================================================

    @Test
    @DisplayName("导出 Excel - 对照表应包含元信息和表头")
    void exportToExcel_mappingSheet_shouldContainMetadataAndHeader() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportToExcel(sampleReport, baos);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
            Sheet sheet = workbook.getSheet("对照表");
            assertThat(sheet).isNotNull();

            // 元信息行
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("报告标题");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).contains("生成时间");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).contains("生成人");

            // 表头行（第4行）
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("序号");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("互联互通数据元标识符");
            assertThat(sheet.getRow(3).getCell(6).getStringCellValue()).isEqualTo("匹配状态");

            // 数据行：数据集分组标题 + 2条数据
            assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).contains("数据集:");
            assertThat(sheet.getRow(5).getCell(0).getNumericCellValue()).isEqualTo(1);
            assertThat(sheet.getRow(5).getCell(1).getStringCellValue()).isEqualTo("DE01.01.001");
            assertThat(sheet.getRow(6).getCell(6).getStringCellValue()).isEqualTo("未匹配");
        }
    }

    // =========================================================================
    // 3. 缺失项工作表验证
    // =========================================================================

    @Test
    @DisplayName("导出 Excel - 缺失项清单应包含未匹配项")
    void exportToExcel_missingSheet_shouldContainUnmatched() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportToExcel(sampleReport, baos);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
            Sheet sheet = workbook.getSheet("缺失项清单");
            assertThat(sheet).isNotNull();

            // 表头
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("数据元标识符");

            // 缺失项数据行
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("DE01.01.002");
            assertThat(sheet.getRow(3).getCell(5).getStringCellValue()).isEqualTo("新建本地项");

            // 合计行
            assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).contains("合计缺失项");
        }
    }

    // =========================================================================
    // 4. 评分摘要工作表验证
    // =========================================================================

    @Test
    @DisplayName("导出 Excel - 评分摘要应包含评分信息")
    void exportToExcel_scoreSheet_shouldContainScores() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportToExcel(sampleReport, baos);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
            Sheet sheet = workbook.getSheet("评分摘要");
            assertThat(sheet).isNotNull();

            // 总体评分
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).contains("总体评分");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).contains("50.0");

            // 按数据集评分表头
            assertThat(sheet.getRow(5).getCell(0).getStringCellValue()).isEqualTo("数据集编码");

            // 数据集评分数据
            assertThat(sheet.getRow(6).getCell(0).getStringCellValue()).isEqualTo("HDSB01.01");

            // 改进建议
            assertThat(sheet.getRow(8).getCell(0).getStringCellValue()).contains("改进建议");
            assertThat(sheet.getRow(9).getCell(0).getStringCellValue()).contains("匹配率为");
        }
    }

    // =========================================================================
    // 5. 空报告导出
    // =========================================================================

    @Test
    @DisplayName("导出 Excel - 空报告也能正常导出")
    void exportToExcel_emptyReport_shouldWork() throws IOException {
        var emptyOverallScore = new OverallScore(0.0, List.of(), List.of("无可用数据集"));
        var emptyReport = new ComplianceReport(
                "空报告", LocalDateTime.now(), "system",
                List.of(), emptyOverallScore);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportToExcel(emptyReport, baos);

        assertThat(baos.size()).isGreaterThan(0);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            Sheet missingSheet = workbook.getSheet("缺失项清单");
            assertThat(missingSheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("无缺失项");
        }
    }

    // =========================================================================
    // 6. Excel 文件完整性
    // =========================================================================

    @Test
    @DisplayName("导出 Excel - 文件可成功写入并读取")
    void exportToExcel_fileShouldBeValidXlsx() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportToExcel(sampleReport, baos);

        byte[] bytes = baos.toByteArray();
        assertThat(bytes).isNotEmpty();
        assertThat(bytes.length).isGreaterThan(1000);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheetAt(0).getPhysicalNumberOfRows()).isGreaterThan(0);
        }
    }
}
