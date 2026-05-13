package io.github.matrixlane.metadataregistry.web.service.datadictionary;

import io.github.matrixlane.metadataregistry.web.dto.export.datadictionary.DataDictionaryItemExport;
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
 * 数据字典 Excel 导出服务
 *
 * <p>使用 Apache POI (XSSFWorkbook) 生成数据字典 Excel (.xlsx) 文件。
 * 单文件多 sheet 模式：每个数据元类型一个 sheet，或所有项统一在一个 sheet。
 */
@Service
public class DataDictionaryExcelExportService {

    private static final Logger log = LoggerFactory.getLogger(DataDictionaryExcelExportService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SCHEMA_VERSION = "1.0.0";

    // ─────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────

    /**
     * 导出数据字典为 Excel 文件，写入指定输出流
     *
     * @param items        数据字典项列表
     * @param outputStream 输出流
     * @param includeValueDomainDetails 是否包含值域详情
     * @param includeRegistrationInfo   是否包含注册信息
     * @throws IOException 如果写入失败
     */
    public void exportToExcel(List<DataDictionaryItemExport> items,
                               OutputStream outputStream,
                               boolean includeValueDomainDetails,
                               boolean includeRegistrationInfo) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle titleStyle = createTitleStyle(workbook);

            createDataDictionarySheet(workbook, items, headerStyle, dataStyle, titleStyle,
                    includeValueDomainDetails, includeRegistrationInfo);

            if (includeValueDomainDetails) {
                createValueDomainDetailSheet(workbook, items, headerStyle, dataStyle);
            }

            // 自动调整列宽
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                if (sheet.getPhysicalNumberOfRows() > 0) {
                    Row firstRow = sheet.getRow(0);
                    if (firstRow != null) {
                        int lastCol = firstRow.getLastCellNum();
                        for (int c = 0; c < Math.min(lastCol, 15); c++) {
                            sheet.autoSizeColumn(c);
                        }
                    }
                }
            }

            workbook.write(outputStream);
            outputStream.flush();
        }
    }

    /**
     * 导出大数据量数据字典，使用 SXSSFWorkbook 流式写入
     */
    public void exportToExcelStreaming(List<DataDictionaryItemExport> items,
                                        OutputStream outputStream,
                                        boolean includeValueDomainDetails,
                                        boolean includeRegistrationInfo) throws IOException {
        // SXSSFWorkbook 用于大数据量，避免内存溢出
        try (Workbook workbook = new org.apache.poi.xssf.streaming.SXSSFWorkbook(100)) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            createDataDictionarySheet(workbook, items, headerStyle, dataStyle, null,
                    includeValueDomainDetails, includeRegistrationInfo);

            workbook.write(outputStream);
            outputStream.flush();
            ((org.apache.poi.xssf.streaming.SXSSFWorkbook) workbook).dispose();
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Sheet builders
    // ─────────────────────────────────────────────────────────────────

    private void createDataDictionarySheet(Workbook workbook,
                                            List<DataDictionaryItemExport> items,
                                            CellStyle headerStyle,
                                            CellStyle dataStyle,
                                            CellStyle titleStyle,
                                            boolean includeValueDomainDetails,
                                            boolean includeRegistrationInfo) {
        Sheet sheet = workbook.createSheet("数据字典");

        // 标题行
        if (titleStyle != null) {
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("数据字典导出");
            titleCell.setCellStyle(titleStyle);
        }

        // 元信息行
        int rowIdx = (titleStyle != null) ? 1 : 0;
        Row metaRow = sheet.createRow(rowIdx++);
        metaRow.createCell(0).setCellValue("导出时间: " + java.time.LocalDateTime.now().format(DATE_FORMATTER));
        metaRow.createCell(2).setCellValue("记录数: " + items.size());

        // 空行
        rowIdx++;

        // 表头
        String[] baseHeaders = {"序号", "类型", "标识符", "版本", "名称", "定义"};
        String[] vdHeaders = includeValueDomainDetails
                ? new String[]{"值域名称", "数据类型", "长度", "格式", "值域代码", "值域含义"}
                : new String[]{"值域名称"};
        String[] regHeaders = includeRegistrationInfo
                ? new String[]{"注册状态", "创建时间", "最后修改时间", "变更说明"}
                : new String[]{};

        String[] allHeaders = concatArrays(baseHeaders, vdHeaders, regHeaders);

        Row headerRow = sheet.createRow(rowIdx++);
        for (int i = 0; i < allHeaders.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(allHeaders[i]);
            cell.setCellStyle(headerStyle);
        }

        // 数据行
        int seq = 1;
        for (DataDictionaryItemExport item : items) {
            Row dataRow = sheet.createRow(rowIdx++);
            int col = 0;

            dataRow.createCell(col++).setCellValue(seq++);
            dataRow.createCell(col++).setCellValue(nvl(item.itemType()));
            dataRow.createCell(col++).setCellValue(nvl(item.identifier()));
            dataRow.createCell(col++).setCellValue(nvl(item.version()));
            dataRow.createCell(col++).setCellValue(nvl(item.label()));
            dataRow.createCell(col++).setCellValue(nvl(item.definition()));

            if (includeValueDomainDetails) {
                dataRow.createCell(col++).setCellValue(nvl(item.valueDomainLabel()));
                dataRow.createCell(col++).setCellValue(nvl(item.dataTypeLabel()));
                dataRow.createCell(col++).setCellValue(item.maximumCharacterQuantity() != null ? item.maximumCharacterQuantity().toString() : "");
                dataRow.createCell(col++).setCellValue(nvl(item.format()));
                String pvValues = formatPermissibleValues(item.permissibleValues(), true);
                String pvMeanings = formatPermissibleValues(item.permissibleValues(), false);
                dataRow.createCell(col++).setCellValue(pvValues);
                dataRow.createCell(col++).setCellValue(pvMeanings);
            } else {
                dataRow.createCell(col++).setCellValue(nvl(item.valueDomainLabel()));
            }

            if (includeRegistrationInfo) {
                dataRow.createCell(col++).setCellValue(item.registrationStatus() != null ? item.registrationStatus().name() : "");
                dataRow.createCell(col++).setCellValue(item.creationDateTime() != null ? item.creationDateTime().format(DATE_FORMATTER) : "");
                dataRow.createCell(col++).setCellValue(item.lastChangeDateTime() != null ? item.lastChangeDateTime().format(DATE_FORMATTER) : "");
                dataRow.createCell(col++).setCellValue(nvl(item.changeDescription()));
            }

            for (int i = 0; i < col; i++) {
                Cell cell = dataRow.getCell(i);
                if (cell != null && dataStyle != null) {
                    cell.setCellStyle(dataStyle);
                }
            }
        }
    }

    private void createValueDomainDetailSheet(Workbook workbook,
                                               List<DataDictionaryItemExport> items,
                                               CellStyle headerStyle,
                                               CellStyle dataStyle) {
        Sheet sheet = workbook.createSheet("值域明细");

        String[] headers = {"序号", "数据元标识符", "数据元名称", "值代码", "值含义", "值说明"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        int seq = 1;
        for (DataDictionaryItemExport item : items) {
            if (item.permissibleValues() == null || item.permissibleValues().isEmpty()) {
                continue;
            }
            for (DataDictionaryItemExport.PermissibleValueExport pv : item.permissibleValues()) {
                Row dataRow = sheet.createRow(rowIdx++);
                dataRow.createCell(0).setCellValue(seq++);
                dataRow.createCell(1).setCellValue(nvl(item.identifier()));
                dataRow.createCell(2).setCellValue(nvl(item.label()));
                dataRow.createCell(3).setCellValue(nvl(pv.value()));
                dataRow.createCell(4).setCellValue(nvl(pv.meaning()));
                dataRow.createCell(5).setCellValue(nvl(pv.description()));

                for (int i = 0; i < headers.length; i++) {
                    Cell cell = dataRow.getCell(i);
                    if (cell != null && dataStyle != null) {
                        cell.setCellStyle(dataStyle);
                    }
                }
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
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    // ─────────────────────────────────────────────────────────────────
    // Utility helpers
    // ─────────────────────────────────────────────────────────────────

    private static String nvl(String value) {
        return value != null ? value : "";
    }

    private static String formatPermissibleValues(List<DataDictionaryItemExport.PermissibleValueExport> pvs,
                                                   boolean values) {
        if (pvs == null || pvs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pvs.size(); i++) {
            DataDictionaryItemExport.PermissibleValueExport pv = pvs.get(i);
            String val = values ? pv.value() : pv.meaning();
            if (val != null) {
                sb.append(val);
                if (i < pvs.size() - 1) {
                    sb.append("; ");
                }
            }
        }
        return sb.toString();
    }

    private static String[] concatArrays(String[]... arrays) {
        int totalLen = 0;
        for (String[] arr : arrays) {
            totalLen += arr.length;
        }
        String[] result = new String[totalLen];
        int pos = 0;
        for (String[] arr : arrays) {
            System.arraycopy(arr, 0, result, pos, arr.length);
            pos += arr.length;
        }
        return result;
    }
}
