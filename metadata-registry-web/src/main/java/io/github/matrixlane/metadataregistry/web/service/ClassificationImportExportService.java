package io.github.matrixlane.metadataregistry.web.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import io.github.matrixlane.metadataregistry.dataspecification.ClassificationNode;
import io.github.matrixlane.metadataregistry.dataspecification.ClassificationScheme;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.classification.ClassificationImportResult;
import io.github.matrixlane.metadataregistry.web.dto.classification.ClassificationImportRow;
import io.github.matrixlane.metadataregistry.web.dto.classification.ImportErrorDetail;
import io.github.matrixlane.metadataregistry.web.repository.ClassificationNodeRepository;
import io.github.matrixlane.metadataregistry.web.repository.ClassificationSchemeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassificationImportExportService {

    private static final Pattern NODE_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_\\-\\.]+$");
    private static final int MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB
    private static final int MAX_NODE_NAME_LENGTH = 255;
    private static final int MAX_NODE_CODE_LENGTH = 255;
    private static final int MAX_DESCRIPTION_LENGTH = 4000;
    private static final int MAX_DEFINITION_LENGTH = 4000;

    // Column headers for import/export
    public static final String COL_SCHEME_NAME = "scheme_name";
    public static final String COL_SCHEME_DESCRIPTION = "scheme_description";
    public static final String COL_SCHEME_VERSION = "scheme_version";
    public static final String COL_SCHEME_STATUS = "scheme_status";
    public static final String COL_NODE_CODE = "node_code";
    public static final String COL_NODE_NAME = "node_name";
    public static final String COL_NODE_DESCRIPTION = "node_description";
    public static final String COL_NODE_DEFINITION = "node_definition";
    public static final String COL_PARENT_CODE = "parent_code";
    public static final String COL_SORT_ORDER = "sort_order";

    private static final String[] CSV_HEADERS = {
            COL_SCHEME_NAME, COL_SCHEME_DESCRIPTION, COL_SCHEME_VERSION, COL_SCHEME_STATUS,
            COL_NODE_CODE, COL_NODE_NAME, COL_NODE_DESCRIPTION, COL_NODE_DEFINITION,
            COL_PARENT_CODE, COL_SORT_ORDER
    };

    private final ClassificationSchemeRepository schemeRepository;
    private final ClassificationNodeRepository nodeRepository;

    // ==================== Import: Two-phase (prevalidate + import) ====================

    /**
     * Phase 1: Pre-validate import file without any database changes.
     * Returns a result with errorCount > 0 if validation fails.
     */
    @Transactional(readOnly = true)
    public ClassificationImportResult prevalidateImport(MultipartFile file, UUID targetSchemeId) {
        List<ClassificationImportRow> rows = parseFile(file);
        List<ImportErrorDetail> errors = validateRows(rows, targetSchemeId, true);
        String schemeName = deriveSchemeName(rows, targetSchemeId);
        return new ClassificationImportResult(
                errors.isEmpty(),
                targetSchemeId,
                schemeName,
                rows.size(),
                0,
                errors.size(),
                errors
        );
    }

    /**
     * Phase 2: Import validated file into database.
     * Creates scheme if needed, then creates nodes in topological order (parents before children).
     */
    @Transactional
    public ClassificationImportResult importFromFile(MultipartFile file, UUID targetSchemeId) {
        List<ClassificationImportRow> rows = parseFile(file);
        List<ImportErrorDetail> errors = validateRows(rows, targetSchemeId, false);
        if (!errors.isEmpty()) {
            String schemeName = deriveSchemeName(rows, targetSchemeId);
            return new ClassificationImportResult(
                    false,
                    targetSchemeId,
                    schemeName,
                    rows.size(),
                    0,
                    errors.size(),
                    errors
            );
        }

        // Determine or create scheme
        ClassificationScheme scheme;
        if (targetSchemeId != null) {
            scheme = schemeRepository.findById(targetSchemeId)
                    .orElseThrow(() -> new BusinessException(ApiCodes.NOT_FOUND,
                            "目标分类方案不存在: " + targetSchemeId));
        } else {
            String schemeName = rows.stream()
                    .map(ClassificationImportRow::schemeName)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ApiCodes.VALIDATION_FAILED,
                            "未指定目标分类方案且文件中无 scheme_name"));

            Optional<ClassificationScheme> existing = schemeRepository.findAll().stream()
                    .filter(s -> s.getSchemeName().equalsIgnoreCase(schemeName))
                    .findFirst();

            if (existing.isPresent()) {
                scheme = existing.get();
            } else {
                ClassificationScheme newScheme = new ClassificationScheme(schemeName);
                String desc = rows.stream().map(ClassificationImportRow::schemeDescription)
                        .filter(StringUtils::hasText).findFirst().orElse(null);
                String ver = rows.stream().map(ClassificationImportRow::schemeVersion)
                        .filter(StringUtils::hasText).findFirst().orElse(null);
                String st = rows.stream().map(ClassificationImportRow::schemeStatus)
                        .filter(StringUtils::hasText).findFirst().orElse("DRAFT");
                newScheme.setDescription(desc);
                newScheme.setVersion(ver);
                newScheme.setStatus(st);
                scheme = schemeRepository.save(newScheme);
            }
        }

        // Build topological order and persist nodes
        List<ClassificationImportRow> ordered = topologicalSort(rows);
        Map<String, ClassificationNode> codeToNode = new HashMap<>();
        int importedCount = 0;

        for (ClassificationImportRow row : ordered) {
            ClassificationNode node = new ClassificationNode(row.nodeCode(), row.nodeName(), scheme);
            node.setDescription(row.nodeDescription());
            node.setDefinition(row.nodeDefinition());
            node.setSortOrder(row.sortOrder() != null ? row.sortOrder() : 0);

            if (StringUtils.hasText(row.parentCode())) {
                ClassificationNode parent = codeToNode.get(row.parentCode());
                if (parent != null) {
                    node.setParent(parent);
                }
            }

            ClassificationNode saved = nodeRepository.save(node);
            codeToNode.put(saved.getNodeCode(), saved);
            importedCount++;
        }

        return new ClassificationImportResult(
                true,
                scheme.getId(),
                scheme.getSchemeName(),
                rows.size(),
                importedCount,
                0,
                Collections.emptyList()
        );
    }

    // ==================== Export ====================

    /**
     * Export a classification scheme (with all nodes) to Excel (.xlsx) bytes.
     */
    @Transactional(readOnly = true)
    public byte[] exportToExcel(UUID schemeId) {
        ClassificationScheme scheme = schemeRepository.findById(schemeId)
                .orElseThrow(() -> new BusinessException(ApiCodes.NOT_FOUND,
                        "分类方案不存在: " + schemeId));
        List<ClassificationNode> nodes = nodeRepository
                .findByClassificationSchemeIdOrderBySortOrderAsc(schemeId);

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Classification");

            // Header row
            Row header = sheet.createRow(0);
            for (int i = 0; i < CSV_HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(CSV_HEADERS[i]);
            }

            // Data rows
            Map<UUID, String> idToCode = nodes.stream()
                    .collect(Collectors.toMap(ClassificationNode::getId, ClassificationNode::getNodeCode));

            int rowIdx = 1;
            for (ClassificationNode node : nodes) {
                Row row = sheet.createRow(rowIdx++);
                writeExportRow(row, scheme, node, idToCode.get(node.getParentId()));
            }

            // Note: autoSizeColumn skipped for SXSSFWorkbook streaming compatibility

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ApiCodes.INTERNAL_ERROR,
                    "Excel 导出失败: " + e.getMessage());
        }
    }

    /**
     * Export a classification scheme (with all nodes) to CSV bytes.
     */
    @Transactional(readOnly = true)
    public byte[] exportToCsv(UUID schemeId) {
        ClassificationScheme scheme = schemeRepository.findById(schemeId)
                .orElseThrow(() -> new BusinessException(ApiCodes.NOT_FOUND,
                        "分类方案不存在: " + schemeId));
        List<ClassificationNode> nodes = nodeRepository
                .findByClassificationSchemeIdOrderBySortOrderAsc(schemeId);

        StringWriter writer = new StringWriter();
        try (CSVWriter csvWriter = new CSVWriter(writer,
                CSVWriter.DEFAULT_SEPARATOR,
                CSVWriter.NO_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END)) {

            csvWriter.writeNext(CSV_HEADERS);

            Map<UUID, String> idToCode = nodes.stream()
                    .collect(Collectors.toMap(ClassificationNode::getId, ClassificationNode::getNodeCode));

            for (ClassificationNode node : nodes) {
                String[] row = buildCsvRow(scheme, node, idToCode.get(node.getParentId()));
                csvWriter.writeNext(row);
            }
        } catch (IOException e) {
            throw new BusinessException(ApiCodes.INTERNAL_ERROR,
                    "CSV 导出失败: " + e.getMessage());
        }

        return writer.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ==================== File Parsing ====================

    List<ClassificationImportRow> parseFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ApiCodes.BAD_REQUEST, "上传文件为空");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(ApiCodes.BAD_REQUEST,
                    "文件大小超过 10MB 限制: " + file.getSize());
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        try {
            if (filename.toLowerCase().endsWith(".xlsx")) {
                return parseExcel(file.getInputStream());
            } else if (filename.toLowerCase().endsWith(".csv")) {
                return parseCsv(file.getInputStream());
            } else {
                throw new BusinessException(ApiCodes.BAD_REQUEST,
                        "不支持的文件格式，仅支持 .xlsx 和 .csv");
            }
        } catch (IOException e) {
            throw new BusinessException(ApiCodes.BAD_REQUEST,
                    "文件读取失败: " + e.getMessage());
        }
    }

    List<ClassificationImportRow> parseExcel(InputStream inputStream) throws IOException {
        List<ClassificationImportRow> rows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() <= 1) {
                return rows; // Only header or empty
            }

            Map<String, Integer> headerMap = parseHeaderMap(sheet.getRow(0));
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isEmptyRow(row, headerMap)) {
                    continue;
                }
                rows.add(extractRow(row, headerMap, i + 1));
            }
        }
        return rows;
    }

    List<ClassificationImportRow> parseCsv(InputStream inputStream) throws IOException {
        List<ClassificationImportRow> rows = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String[] header = reader.readNext();
            if (header == null) {
                return rows;
            }
            Map<String, Integer> headerMap = new LinkedHashMap<>();
            for (int i = 0; i < header.length; i++) {
                headerMap.put(normalizeHeader(header[i]), i);
            }

            String[] line;
            int rowNum = 2;
            while ((line = reader.readNext()) != null) {
                if (isEmptyLine(line)) {
                    continue;
                }
                rows.add(extractCsvRow(line, headerMap, rowNum));
                rowNum++;
            }
        } catch (com.opencsv.exceptions.CsvValidationException e) {
            throw new BusinessException(ApiCodes.BAD_REQUEST,
                    "CSV 解析失败: " + e.getMessage());
        }
        return rows;
    }

    // ==================== Validation ====================

    List<ImportErrorDetail> validateRows(List<ClassificationImportRow> rows,
                                             UUID targetSchemeId,
                                             boolean isPrevalidate) {
        List<ImportErrorDetail> errors = new ArrayList<>();
        if (rows.isEmpty()) {
            errors.add(new ImportErrorDetail(0, null, "EMPTY_FILE",
                    "文件中没有数据行", "请确保文件包含至少一条数据"));
            return errors;
        }

        // Check scheme info
        boolean hasSchemeInfo = rows.stream()
                .anyMatch(r -> StringUtils.hasText(r.schemeName()));
        if (targetSchemeId == null && !hasSchemeInfo) {
            errors.add(new ImportErrorDetail(0, null, "MISSING_SCHEME",
                    "未指定目标分类方案且文件中无 scheme_name", "请在文件中提供 scheme_name 或指定目标方案 ID"));
        }

        // Check for duplicate node codes within the file
        Map<String, List<Integer>> codeToRows = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            ClassificationImportRow row = rows.get(i);
            if (StringUtils.hasText(row.nodeCode())) {
                codeToRows.computeIfAbsent(row.nodeCode(), k -> new ArrayList<>()).add(i + 2);
            }
        }
        for (Map.Entry<String, List<Integer>> entry : codeToRows.entrySet()) {
            if (entry.getValue().size() > 1) {
                for (int rowNum : entry.getValue()) {
                    errors.add(new ImportErrorDetail(rowNum, COL_NODE_CODE, "DUPLICATE_CODE",
                            "文件中节点编码重复: " + entry.getKey(),
                            "请确保文件中节点编码唯一"));
                }
            }
        }

        // Check for duplicate node names under same parent within the file
        Map<String, List<Integer>> nameParentToRows = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            ClassificationImportRow row = rows.get(i);
            if (StringUtils.hasText(row.nodeName())) {
                String key = row.nodeName() + "|" + (row.parentCode() != null ? row.parentCode() : "");
                nameParentToRows.computeIfAbsent(key, k -> new ArrayList<>()).add(i + 2);
            }
        }
        for (Map.Entry<String, List<Integer>> entry : nameParentToRows.entrySet()) {
            if (entry.getValue().size() > 1) {
                for (int rowNum : entry.getValue()) {
                    errors.add(new ImportErrorDetail(rowNum, COL_NODE_NAME, "DUPLICATE_NAME",
                            "同一父节点下子节点名称重复", "请确保同一父节点下子节点名称唯一"));
                }
            }
        }

        // Validate each row
        Set<String> validParentCodes = new HashSet<>();
        for (ClassificationImportRow row : rows) {
            validParentCodes.add(row.nodeCode());
        }

        UUID effectiveSchemeId = targetSchemeId;
        if (effectiveSchemeId == null && hasSchemeInfo) {
            String schemeName = rows.get(0).schemeName();
            Optional<ClassificationScheme> existing = schemeRepository.findAll().stream()
                    .filter(s -> s.getSchemeName().equalsIgnoreCase(schemeName))
                    .findFirst();
            if (existing.isPresent()) {
                effectiveSchemeId = existing.get().getId();
            }
        }

        for (int i = 0; i < rows.size(); i++) {
            int rowNum = i + 2;
            ClassificationImportRow row = rows.get(i);
            validateSingleRow(row, rowNum, errors, validParentCodes, effectiveSchemeId, isPrevalidate);
        }

        // Cycle detection
        List<ImportErrorDetail> cycleErrors = detectCycles(rows);
        errors.addAll(cycleErrors);

        return errors;
    }

    private void validateSingleRow(ClassificationImportRow row, int rowNum,
                                   List<ImportErrorDetail> errors,
                                   Set<String> validParentCodes,
                                   UUID schemeId,
                                   boolean isPrevalidate) {
        // node_code required
        if (!StringUtils.hasText(row.nodeCode())) {
            errors.add(new ImportErrorDetail(rowNum, COL_NODE_CODE, "REQUIRED",
                    "节点编码不能为空", "请填写节点编码"));
        } else {
            if (row.nodeCode().length() > MAX_NODE_CODE_LENGTH) {
                errors.add(new ImportErrorDetail(rowNum, COL_NODE_CODE, "TOO_LONG",
                        "节点编码长度超过 " + MAX_NODE_CODE_LENGTH + " 字符", "请缩短节点编码"));
            }
            if (!NODE_CODE_PATTERN.matcher(row.nodeCode()).matches()) {
                errors.add(new ImportErrorDetail(rowNum, COL_NODE_CODE, "INVALID_FORMAT",
                        "节点编码只能包含字母、数字、下划线、连字符和点号", "请修正节点编码格式"));
            }
            // Check DB uniqueness (only if we have schemeId)
            if (schemeId != null && !isPrevalidate) {
                if (nodeRepository.existsByClassificationSchemeIdAndNodeCode(schemeId, row.nodeCode())) {
                    errors.add(new ImportErrorDetail(rowNum, COL_NODE_CODE, "ALREADY_EXISTS",
                            "同一分类方案下节点编码已存在: " + row.nodeCode(), "请使用不同的节点编码"));
                }
            }
        }

        // node_name required
        if (!StringUtils.hasText(row.nodeName())) {
            errors.add(new ImportErrorDetail(rowNum, COL_NODE_NAME, "REQUIRED",
                    "节点名称不能为空", "请填写节点名称"));
        } else if (row.nodeName().length() > MAX_NODE_NAME_LENGTH) {
            errors.add(new ImportErrorDetail(rowNum, COL_NODE_NAME, "TOO_LONG",
                    "节点名称长度超过 " + MAX_NODE_NAME_LENGTH + " 字符", "请缩短节点名称"));
        }

        // description length
        if (StringUtils.hasText(row.nodeDescription()) && row.nodeDescription().length() > MAX_DESCRIPTION_LENGTH) {
            errors.add(new ImportErrorDetail(rowNum, COL_NODE_DESCRIPTION, "TOO_LONG",
                    "节点描述长度超过 " + MAX_DESCRIPTION_LENGTH + " 字符", "请缩短节点描述"));
        }

        // definition length
        if (StringUtils.hasText(row.nodeDefinition()) && row.nodeDefinition().length() > MAX_DEFINITION_LENGTH) {
            errors.add(new ImportErrorDetail(rowNum, COL_NODE_DEFINITION, "TOO_LONG",
                    "节点定义长度超过 " + MAX_DEFINITION_LENGTH + " 字符", "请缩短节点定义"));
        }

        // parent_code: if provided, must exist as a node in the file or in DB
        if (StringUtils.hasText(row.parentCode())) {
            if (!validParentCodes.contains(row.parentCode())) {
                // Check if exists in DB
                boolean existsInDb = false;
                if (schemeId != null) {
                    existsInDb = nodeRepository.existsByClassificationSchemeIdAndNodeCode(schemeId, row.parentCode());
                }
                if (!existsInDb) {
                    errors.add(new ImportErrorDetail(rowNum, COL_PARENT_CODE, "PARENT_NOT_FOUND",
                            "父节点编码不存在: " + row.parentCode(), "请确保父节点编码在文件中或数据库中存在"));
                }
            }
        }

        // sort_order must be integer if provided
        if (row.sortOrder() != null && row.sortOrder() < 0) {
            errors.add(new ImportErrorDetail(rowNum, COL_SORT_ORDER, "INVALID_VALUE",
                    "排序号不能为负数", "请使用非负整数"));
        }
    }

    List<ImportErrorDetail> detectCycles(List<ClassificationImportRow> rows) {
        List<ImportErrorDetail> errors = new ArrayList<>();
        Map<String, String> codeToParent = new HashMap<>();
        for (ClassificationImportRow row : rows) {
            if (StringUtils.hasText(row.nodeCode())) {
                codeToParent.put(row.nodeCode(), row.parentCode());
            }
        }

        for (String code : codeToParent.keySet()) {
            Set<String> visited = new HashSet<>();
            String current = code;
            while (StringUtils.hasText(current)) {
                if (visited.contains(current)) {
                    // Find which row has this code
                    int rowNum = findRowNumber(rows, current);
                    errors.add(new ImportErrorDetail(rowNum, COL_PARENT_CODE, "CIRCULAR_REFERENCE",
                            "检测到循环引用，节点编码: " + code, "请检查 parent_code 链，确保无循环"));
                    break;
                }
                visited.add(current);
                current = codeToParent.get(current);
            }
        }
        return errors;
    }

    // ==================== Topological Sort ====================

    List<ClassificationImportRow> topologicalSort(List<ClassificationImportRow> rows) {
        Map<String, ClassificationImportRow> codeToRow = new LinkedHashMap<>();
        Map<String, List<String>> graph = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (ClassificationImportRow row : rows) {
            codeToRow.put(row.nodeCode(), row);
            inDegree.put(row.nodeCode(), 0);
        }

        for (ClassificationImportRow row : rows) {
            String code = row.nodeCode();
            graph.putIfAbsent(code, new ArrayList<>());
            if (StringUtils.hasText(row.parentCode()) && codeToRow.containsKey(row.parentCode())) {
                graph.computeIfAbsent(row.parentCode(), k -> new ArrayList<>()).add(code);
                inDegree.put(code, inDegree.get(code) + 1);
            }
        }

        List<ClassificationImportRow> result = new ArrayList<>();
        List<String> queue = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        while (!queue.isEmpty()) {
            String code = queue.remove(0);
            ClassificationImportRow row = codeToRow.get(code);
            if (row != null) {
                result.add(row);
            }
            for (String child : graph.getOrDefault(code, Collections.emptyList())) {
                int newDegree = inDegree.get(child) - 1;
                inDegree.put(child, newDegree);
                if (newDegree == 0) {
                    queue.add(child);
                }
            }
        }

        // If there are remaining nodes, there's a cycle (should have been caught by validation)
        for (ClassificationImportRow row : rows) {
            if (!result.contains(row)) {
                result.add(row);
            }
        }

        return result;
    }

    // ==================== Helpers ====================

    private Map<String, Integer> parseHeaderMap(Row headerRow) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String value = getCellStringValue(cell);
            if (StringUtils.hasText(value)) {
                map.put(normalizeHeader(value), cell.getColumnIndex());
            }
        }
        return map;
    }

    private String normalizeHeader(String header) {
        return header.trim().toLowerCase().replaceAll("\\s+", "_");
    }

    private ClassificationImportRow extractRow(Row row, Map<String, Integer> headerMap, int rowNum) {
        String schemeName = getCellString(row, headerMap, COL_SCHEME_NAME);
        String schemeDesc = getCellString(row, headerMap, COL_SCHEME_DESCRIPTION);
        String schemeVer = getCellString(row, headerMap, COL_SCHEME_VERSION);
        String schemeStatus = getCellString(row, headerMap, COL_SCHEME_STATUS);
        String nodeCode = getCellString(row, headerMap, COL_NODE_CODE);
        String nodeName = getCellString(row, headerMap, COL_NODE_NAME);
        String nodeDesc = getCellString(row, headerMap, COL_NODE_DESCRIPTION);
        String nodeDef = getCellString(row, headerMap, COL_NODE_DEFINITION);
        String parentCode = getCellString(row, headerMap, COL_PARENT_CODE);
        Integer sortOrder = getCellInt(row, headerMap, COL_SORT_ORDER);

        return new ClassificationImportRow(schemeName, schemeDesc, schemeVer, schemeStatus,
                nodeCode, nodeName, nodeDesc, nodeDef, parentCode, sortOrder);
    }

    private ClassificationImportRow extractCsvRow(String[] line, Map<String, Integer> headerMap, int rowNum) {
        String schemeName = getCsvValue(line, headerMap, COL_SCHEME_NAME);
        String schemeDesc = getCsvValue(line, headerMap, COL_SCHEME_DESCRIPTION);
        String schemeVer = getCsvValue(line, headerMap, COL_SCHEME_VERSION);
        String schemeStatus = getCsvValue(line, headerMap, COL_SCHEME_STATUS);
        String nodeCode = getCsvValue(line, headerMap, COL_NODE_CODE);
        String nodeName = getCsvValue(line, headerMap, COL_NODE_NAME);
        String nodeDesc = getCsvValue(line, headerMap, COL_NODE_DESCRIPTION);
        String nodeDef = getCsvValue(line, headerMap, COL_NODE_DEFINITION);
        String parentCode = getCsvValue(line, headerMap, COL_PARENT_CODE);
        Integer sortOrder = parseIntOrNull(getCsvValue(line, headerMap, COL_SORT_ORDER));

        return new ClassificationImportRow(schemeName, schemeDesc, schemeVer, schemeStatus,
                nodeCode, nodeName, nodeDesc, nodeDef, parentCode, sortOrder);
    }

    private String getCellString(Row row, Map<String, Integer> headerMap, String colName) {
        Integer idx = headerMap.get(colName);
        if (idx == null || idx >= row.getLastCellNum()) {
            return null;
        }
        Cell cell = row.getCell(idx);
        return getCellStringValue(cell);
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d)) {
                    yield String.valueOf((long) d);
                }
                yield String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getStringCellValue() != null ? cell.getStringCellValue().trim() : null;
            case BLANK -> null;
            default -> null;
        };
    }

    private Integer getCellInt(Row row, Map<String, Integer> headerMap, String colName) {
        String val = getCellString(row, headerMap, colName);
        return parseIntOrNull(val);
    }

    private String getCsvValue(String[] line, Map<String, Integer> headerMap, String colName) {
        Integer idx = headerMap.get(colName);
        if (idx == null || idx >= line.length) {
            return null;
        }
        String val = line[idx];
        return val != null ? val.trim() : null;
    }

    private Integer parseIntOrNull(String val) {
        if (!StringUtils.hasText(val)) {
            return null;
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isEmptyRow(Row row, Map<String, Integer> headerMap) {
        Integer nodeCodeIdx = headerMap.get(COL_NODE_CODE);
        if (nodeCodeIdx == null) {
            return true;
        }
        Cell cell = row.getCell(nodeCodeIdx);
        return cell == null || cell.getCellType() == CellType.BLANK ||
                !StringUtils.hasText(getCellStringValue(cell));
    }

    private boolean isEmptyLine(String[] line) {
        if (line == null || line.length == 0) {
            return true;
        }
        for (String val : line) {
            if (StringUtils.hasText(val)) {
                return false;
            }
        }
        return true;
    }

    private int findRowNumber(List<ClassificationImportRow> rows, String code) {
        for (int i = 0; i < rows.size(); i++) {
            if (code.equals(rows.get(i).nodeCode())) {
                return i + 2;
            }
        }
        return 0;
    }

    private String deriveSchemeName(List<ClassificationImportRow> rows, UUID targetSchemeId) {
        if (targetSchemeId != null) {
            return schemeRepository.findById(targetSchemeId)
                    .map(ClassificationScheme::getSchemeName)
                    .orElse("");
        }
        return rows.stream()
                .map(ClassificationImportRow::schemeName)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private void writeExportRow(Row row, ClassificationScheme scheme, ClassificationNode node, String parentCode) {
        row.createCell(0).setCellValue(scheme.getSchemeName());
        row.createCell(1).setCellValue(scheme.getDescription() != null ? scheme.getDescription() : "");
        row.createCell(2).setCellValue(scheme.getVersion() != null ? scheme.getVersion() : "");
        row.createCell(3).setCellValue(scheme.getStatus() != null ? scheme.getStatus() : "");
        row.createCell(4).setCellValue(node.getNodeCode());
        row.createCell(5).setCellValue(node.getNodeName());
        row.createCell(6).setCellValue(node.getDescription() != null ? node.getDescription() : "");
        row.createCell(7).setCellValue(node.getDefinition() != null ? node.getDefinition() : "");
        row.createCell(8).setCellValue(parentCode != null ? parentCode : "");
        row.createCell(9).setCellValue(node.getSortOrder() != null ? node.getSortOrder() : 0);
    }

    private String[] buildCsvRow(ClassificationScheme scheme, ClassificationNode node, String parentCode) {
        return new String[]{
                scheme.getSchemeName(),
                scheme.getDescription() != null ? scheme.getDescription() : "",
                scheme.getVersion() != null ? scheme.getVersion() : "",
                scheme.getStatus() != null ? scheme.getStatus() : "",
                node.getNodeCode(),
                node.getNodeName(),
                node.getDescription() != null ? node.getDescription() : "",
                node.getDefinition() != null ? node.getDefinition() : "",
                parentCode != null ? parentCode : "",
                node.getSortOrder() != null ? String.valueOf(node.getSortOrder()) : "0"
        };
    }
}
