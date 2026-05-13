package io.github.matrixlane.metadataregistry.web.service.compliancereport;

import io.github.matrixlane.metadataregistry.web.dto.compliancereport.ComplianceReport;
import io.github.matrixlane.metadataregistry.web.dto.compliancereport.DataSetReport;
import io.github.matrixlane.metadataregistry.web.dto.compliancereport.MappingEntry;
import io.github.matrixlane.metadataregistry.web.dto.compliancereport.MissingEntry;
import io.github.matrixlane.metadataregistry.web.dto.compliancereport.OverallScore;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Excel 合规报告导出服务.
 *
 * <p>使用 Apache POI (XSSFWorkbook) 生成包含多个工作表的 Excel (.xlsx) 文件。
 * 符合架构评审决策：使用 XSSFWorkbook（非 SXSSF），资源释放逻辑完备。
 *
 * <p>工作表结构：
 * <ol>
 *   <li>对照表 — 全部/筛选后的映射条目</li>
 *   <li>缺失项 — 未匹配的数据元清单</li>
 *   <li>评分摘要 — 总体评分、按数据集评分、改进建议</li>
 * </ol>
 */
@Service
public class ExcelExportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelExportService.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ─────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────

    /**
     * 将合规报告导出为 Excel 文件，写入指定输出流.
     *
     * @param report       合规报告
     * @param outputStream 输出流（响应流或文件流）
     * @throws IOException 如果写入失败
     */
    public void exportToExcel(ComplianceReport report, OutputStream outputStream) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            // 创建样式
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle titleStyle = createTitleStyle(workbook);

            // 创建工作表
            createMappingSheet(workbook, report, headerStyle, dataStyle, titleStyle);
            createMissingSheet(workbook, report, headerStyle, dataStyle, titleStyle);
            createScoreSheet(workbook, report, headerStyle, dataStyle, titleStyle);

            // 自动调整列宽（每个工作表的前几列）
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                if (sheet.getPhysicalNumberOfRows() > 0) {
                    int lastCol = sheet.getRow(0).getLastCellNum();
                    for (int c = 0; c < Math.min(lastCol, 10); c++) {
                        sheet.autoSizeColumn(c);
                    }
                }
            }

            workbook.write(outputStream);
            outputStream.flush();
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Sheet builders
    // ─────────────────────────────────────────────────────────────────

    /**
     * 创建工作表 1：对照表.
     */
    private void createMappingSheet(Workbook workbook, ComplianceReport report,
                                     CellStyle headerStyle, CellStyle dataStyle, CellStyle titleStyle) {
        Sheet sheet = workbook.createSheet("对照表");

        // 元信息行
        Row metaRow = sheet.createRow(0);
        metaRow.createCell(0).setCellValue("报告标题: " + report.title());
        metaRow.getCell(0).setCellStyle(titleStyle);
        Row metaRow2 = sheet.createRow(1);
        metaRow2.createCell(0).setCellValue("生成时间: " + report.generatedAt().format(DATE_FORMATTER));
        metaRow2.getCell(0).setCellStyle(titleStyle);
        Row metaRow3 = sheet.createRow(2);
        metaRow3.createCell(0).setCellValue("生成人: " + report.generatedBy());
        metaRow3.getCell(0).setCellStyle(titleStyle);

        // 表头行（第4行，索引3）
        String[] headers = {"序号", "互联互通数据元标识符", "互联互通数据元名称",
                "互联互通定义", "本地对应元数据标识符", "本地对应元数据名称",
                "匹配状态", "备注"};
        Row headerRow = sheet.createRow(3);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // 数据行
        int rowIdx = 4;
        for (DataSetReport dsReport : report.dataSetReports()) {
            // 数据集分组标题
            Row groupRow = sheet.createRow(rowIdx++);
            Cell groupCell = groupRow.createCell(0);
            groupCell.setCellValue("数据集: " + dsReport.dataSetName() + " (" + dsReport.dataSetCode() + ")");
            groupCell.setCellStyle(titleStyle);

            for (MappingEntry entry : dsReport.mappings()) {
                Row dataRow = sheet.createRow(rowIdx++);
                dataRow.createCell(0).setCellValue(entry.sequence());
                dataRow.createCell(1).setCellValue(entry.interopIdentifier());
                dataRow.createCell(2).setCellValue(entry.interopName());
                dataRow.createCell(3).setCellValue(entry.interopDefinition() != null ? entry.interopDefinition() : "");
                dataRow.createCell(4).setCellValue(entry.localIdentifier());
                dataRow.createCell(5).setCellValue(entry.localName());
                dataRow.createCell(6).setCellValue(entry.matchStatus());
                dataRow.createCell(7).setCellValue(entry.remarks() != null ? entry.remarks() : "");

                for (int i = 0; i < headers.length; i++) {
                    Cell cell = dataRow.getCell(i);
                    if (cell != null) {
                        cell.setCellStyle(dataStyle);
                    }
                }
            }
        }
    }

    /**
     * 创建工作表 2：缺失项清单.
     */
    private void createMissingSheet(Workbook workbook, ComplianceReport report,
                                     CellStyle headerStyle, CellStyle dataStyle, CellStyle titleStyle) {
        Sheet sheet = workbook.createSheet("缺失项清单");

        // 元信息行
        Row metaRow = sheet.createRow(0);
        metaRow.createCell(0).setCellValue("报告标题: " + report.title());
        metaRow.getCell(0).setCellStyle(titleStyle);
        Row metaRow2 = sheet.createRow(1);
        metaRow2.createCell(0).setCellValue("生成时间: " + report.generatedAt().format(DATE_FORMATTER));
        metaRow2.getCell(0).setCellStyle(titleStyle);

        // 表头
        String[] headers = {"数据元标识符", "数据元名称", "定义", "所属数据集编码", "所属数据集名称", "建议行动"};
        Row headerRow = sheet.createRow(2);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // 数据行
        int rowIdx = 3;
        int totalMissing = 0;
        for (DataSetReport dsReport : report.dataSetReports()) {
            for (MissingEntry entry : dsReport.missingEntries()) {
                Row dataRow = sheet.createRow(rowIdx++);
                dataRow.createCell(0).setCellValue(entry.deIdentifier());
                dataRow.createCell(1).setCellValue(entry.deName());
                dataRow.createCell(2).setCellValue(entry.deDefinition() != null ? entry.deDefinition() : "");
                dataRow.createCell(3).setCellValue(entry.dataSetCode());
                dataRow.createCell(4).setCellValue(entry.dataSetName());
                dataRow.createCell(5).setCellValue(entry.suggestedAction());

                for (int i = 0; i < headers.length; i++) {
                    Cell cell = dataRow.getCell(i);
                    if (cell != null) {
                        cell.setCellStyle(dataStyle);
                    }
                }
                totalMissing++;
            }
        }

        // 统计摘要行
        if (rowIdx > 3) {
            Row summaryRow = sheet.createRow(rowIdx);
            summaryRow.createCell(0).setCellValue("合计缺失项: " + totalMissing);
            summaryRow.getCell(0).setCellStyle(titleStyle);
        } else {
            Row emptyRow = sheet.createRow(rowIdx);
            emptyRow.createCell(0).setCellValue("无缺失项");
            emptyRow.getCell(0).setCellStyle(titleStyle);
        }
    }

    /**
     * 创建工作表 3：评分摘要.
     */
    private void createScoreSheet(Workbook workbook, ComplianceReport report,
                                   CellStyle headerStyle, CellStyle dataStyle, CellStyle titleStyle) {
        Sheet sheet = workbook.createSheet("评分摘要");

        // 元信息行
        Row metaRow = sheet.createRow(0);
        metaRow.createCell(0).setCellValue("报告标题: " + report.title());
        metaRow.getCell(0).setCellStyle(titleStyle);
        Row metaRow2 = sheet.createRow(1);
        metaRow2.createCell(0).setCellValue("生成时间: " + report.generatedAt().format(DATE_FORMATTER));
        metaRow2.getCell(0).setCellStyle(titleStyle);

        // 总体评分
        OverallScore score = report.overallScore();
        Row overallLabelRow = sheet.createRow(3);
        overallLabelRow.createCell(0).setCellValue("总体评分:");
        overallLabelRow.getCell(0).setCellStyle(titleStyle);
        overallLabelRow.createCell(1).setCellValue(score.overallScore() + " 分");
        overallLabelRow.getCell(1).setCellStyle(titleStyle);

        // 按数据集评分表头
        int rowIdx = 5;
        String[] headers = {"数据集编码", "数据集名称", "匹配率", "质量评分"};
        Row headerRow = sheet.createRow(rowIdx++);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // 按数据集评分数据行
        for (OverallScore.DataSetScore dsScore : score.dataSetScores()) {
            Row dataRow = sheet.createRow(rowIdx++);
            dataRow.createCell(0).setCellValue(dsScore.dataSetCode());
            dataRow.createCell(1).setCellValue(dsScore.dataSetName());
            dataRow.createCell(2).setCellValue(String.format("%.2f%%", dsScore.matchRate() * 100));
            dataRow.createCell(3).setCellValue(dsScore.qualityScore());

            for (int i = 0; i < headers.length; i++) {
                Cell cell = dataRow.getCell(i);
                if (cell != null) {
                    cell.setCellStyle(dataStyle);
                }
            }
        }

        // 改进建议
        rowIdx += 1;
        Row suggestHeaderRow = sheet.createRow(rowIdx++);
        suggestHeaderRow.createCell(0).setCellValue("改进建议:");
        suggestHeaderRow.getCell(0).setCellStyle(titleStyle);

        for (String suggestion : score.suggestions()) {
            Row sugRow = sheet.createRow(rowIdx++);
            sugRow.createCell(0).setCellValue("• " + suggestion);
            Cell cell = sugRow.getCell(0);
            if (cell != null) {
                cell.setCellStyle(dataStyle);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Style helpers
    // ─────────────────────────────────────────────────────────────────

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        return style;
    }
}
