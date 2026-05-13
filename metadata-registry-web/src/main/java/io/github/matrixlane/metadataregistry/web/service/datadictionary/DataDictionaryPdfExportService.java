package io.github.matrixlane.metadataregistry.web.service.datadictionary;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import io.github.matrixlane.metadataregistry.web.dto.export.datadictionary.DataDictionaryItemExport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 数据字典 PDF 导出服务
 *
 * <p>使用 OpenPDF 生成数据字典 PDF 报告。
 * 支持单数据元详情页和完整数据字典报告（含封面、目录、分类章节）。
 */
@Service
public class DataDictionaryPdfExportService {

    private static final Logger log = LoggerFactory.getLogger(DataDictionaryPdfExportService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 中文字体配置 —— 优先使用系统字体，回退到内置字体
    private static final String[] CJK_FONT_PATHS = {
            "C:/Windows/Fonts/simhei.ttf",       // 黑体
            "C:/Windows/Fonts/simsun.ttc",       // 宋体
            "C:/Windows/Fonts/msyh.ttc",         // 微软雅黑
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc", // Linux
            "/System/Library/Fonts/PingFang.ttc"  // macOS
    };

    private final Font titleFont;
    private final Font headingFont;
    private final Font bodyFont;
    private final Font smallFont;
    private final Font headerFont;

    public DataDictionaryPdfExportService() {
        BaseFont baseFont = loadBaseFont();
        this.titleFont = new Font(baseFont, 18, Font.BOLD);
        this.headingFont = new Font(baseFont, 14, Font.BOLD);
        this.bodyFont = new Font(baseFont, 10, Font.NORMAL);
        this.smallFont = new Font(baseFont, 9, Font.NORMAL);
        this.headerFont = new Font(baseFont, 10, Font.BOLD);
    }

    private BaseFont loadBaseFont() {
        for (String path : CJK_FONT_PATHS) {
            try {
                if (java.nio.file.Files.exists(java.nio.file.Path.of(path))) {
                    return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                }
            } catch (Exception e) {
                log.debug("无法加载字体: {}", path);
            }
        }
        // 回退到 Helvetica（不支持中文，但保证不崩溃）
        try {
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED);
        } catch (Exception e) {
            throw new RuntimeException("无法加载任何 PDF 字体", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────

    /**
     * 导出单数据元详情 PDF
     */
    public void exportSingleItemPdf(DataDictionaryItemExport item, OutputStream outputStream) throws IOException {
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        try {
            PdfWriter.getInstance(document, outputStream);
            document.open();

            addSingleItemContent(document, item);

            document.close();
        } catch (DocumentException e) {
            throw new IOException("PDF 生成失败", e);
        }
    }

    /**
     * 导出完整数据字典报告 PDF
     */
    public void exportDataDictionaryReport(List<DataDictionaryItemExport> items,
                                            String reportTitle,
                                            OutputStream outputStream) throws IOException {
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        try {
            PdfWriter.getInstance(document, outputStream);
            document.open();

            // 封面
            addCoverPage(document, reportTitle, items.size());

            document.newPage();

            // 数据字典总表
            addDataDictionaryTable(document, items);

            document.newPage();

            // 各数据元详情
            addItemDetails(document, items);

            document.close();
        } catch (DocumentException e) {
            throw new IOException("PDF 报告生成失败", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Content builders
    // ─────────────────────────────────────────────────────────────────

    private void addSingleItemContent(Document document, DataDictionaryItemExport item) throws DocumentException {
        // 标题
        Paragraph title = new Paragraph("数据元详情", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        // 基本信息表
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 3});

        addTableRow(table, "标识符", nvl(item.identifier()));
        addTableRow(table, "版本", nvl(item.version()));
        addTableRow(table, "名称", nvl(item.label()));
        addTableRow(table, "定义", nvl(item.definition()));
        addTableRow(table, "类型", nvl(item.itemType()));
        addTableRow(table, "数据元概念", nvl(item.dataElementConceptLabel()));
        addTableRow(table, "值域", nvl(item.valueDomainLabel()));
        addTableRow(table, "数据类型", nvl(item.dataTypeLabel()));
        addTableRow(table, "长度", item.maximumCharacterQuantity() != null ? item.maximumCharacterQuantity().toString() : "");
        addTableRow(table, "格式", nvl(item.format()));
        addTableRow(table, "注册状态", item.registrationStatus() != null ? item.registrationStatus().name() : "");
        addTableRow(table, "创建时间", item.creationDateTime() != null ? item.creationDateTime().format(DATE_FORMATTER) : "");
        addTableRow(table, "最后修改", item.lastChangeDateTime() != null ? item.lastChangeDateTime().format(DATE_FORMATTER) : "");
        addTableRow(table, "变更说明", nvl(item.changeDescription()));
        addTableRow(table, "来源", nvl(item.origin()));

        document.add(table);

        // 值域明细
        if (item.permissibleValues() != null && !item.permissibleValues().isEmpty()) {
            Paragraph vdTitle = new Paragraph("值域明细", headingFont);
            vdTitle.setSpacingBefore(20);
            vdTitle.setSpacingAfter(10);
            document.add(vdTitle);

            PdfPTable vdTable = new PdfPTable(3);
            vdTable.setWidthPercentage(100);
            vdTable.setWidths(new float[]{1, 2, 2});

            addHeaderCell(vdTable, "值代码");
            addHeaderCell(vdTable, "值含义");
            addHeaderCell(vdTable, "说明");

            for (DataDictionaryItemExport.PermissibleValueExport pv : item.permissibleValues()) {
                addDataCell(vdTable, nvl(pv.value()));
                addDataCell(vdTable, nvl(pv.meaning()));
                addDataCell(vdTable, nvl(pv.description()));
            }

            document.add(vdTable);
        }
    }

    private void addCoverPage(Document document, String reportTitle, int totalCount) throws DocumentException {
        Paragraph title = new Paragraph(reportTitle, titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(30);
        document.add(title);

        Paragraph subtitle = new Paragraph("数据字典报告", headingFont);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(20);
        document.add(subtitle);

        Paragraph info = new Paragraph(
                "生成时间: " + java.time.LocalDateTime.now().format(DATE_FORMATTER) + "\n"
                        + "记录总数: " + totalCount + " 条",
                bodyFont);
        info.setAlignment(Element.ALIGN_CENTER);
        document.add(info);
    }

    private void addDataDictionaryTable(Document document, List<DataDictionaryItemExport> items) throws DocumentException {
        Paragraph title = new Paragraph("数据字典总表", headingFont);
        title.setSpacingAfter(10);
        document.add(title);

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{0.5f, 1, 1.5f, 2, 1, 1});

        addHeaderCell(table, "序号");
        addHeaderCell(table, "标识符");
        addHeaderCell(table, "名称");
        addHeaderCell(table, "定义");
        addHeaderCell(table, "值域");
        addHeaderCell(table, "状态");

        int seq = 1;
        for (DataDictionaryItemExport item : items) {
            addDataCell(table, String.valueOf(seq++));
            addDataCell(table, nvl(item.identifier()));
            addDataCell(table, nvl(item.label()));
            addDataCell(table, nvl(item.definition()));
            addDataCell(table, nvl(item.valueDomainLabel()));
            addDataCell(table, item.registrationStatus() != null ? item.registrationStatus().name() : "");
        }

        document.add(table);
    }

    private void addItemDetails(Document document, List<DataDictionaryItemExport> items) throws DocumentException {
        for (DataDictionaryItemExport item : items) {
            Paragraph title = new Paragraph(item.label() != null ? item.label() : "未命名", headingFont);
            title.setSpacingBefore(15);
            title.setSpacingAfter(8);
            document.add(title);

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1, 3});

            addTableRow(table, "标识符", nvl(item.identifier()));
            addTableRow(table, "定义", nvl(item.definition()));
            addTableRow(table, "数据类型", nvl(item.dataTypeLabel()));
            addTableRow(table, "值域", nvl(item.valueDomainLabel()));

            document.add(table);

            if (items.indexOf(item) < items.size() - 1) {
                document.add(new Paragraph(" ", smallFont));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Table helpers
    // ─────────────────────────────────────────────────────────────────

    private void addTableRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, headerFont));
        labelCell.setBackgroundColor(new java.awt.Color(220, 230, 241));
        labelCell.setPadding(5);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, bodyFont));
        valueCell.setPadding(5);
        table.addCell(valueCell);
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, headerFont));
        cell.setBackgroundColor(new java.awt.Color(68, 114, 196));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void addDataCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, bodyFont));
        cell.setPadding(4);
        table.addCell(cell);
    }

    private static String nvl(String value) {
        return value != null ? value : "";
    }
}
