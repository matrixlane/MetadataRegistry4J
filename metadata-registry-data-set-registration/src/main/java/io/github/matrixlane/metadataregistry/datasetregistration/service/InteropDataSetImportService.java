package io.github.matrixlane.metadataregistry.datasetregistration.service;

import io.github.matrixlane.metadataregistry.datasetregistration.dto.ImportError;
import io.github.matrixlane.metadataregistry.datasetregistration.dto.ImportResult;
import io.github.matrixlane.metadataregistry.datasetregistration.entity.InteropDataElement;
import io.github.matrixlane.metadataregistry.datasetregistration.entity.InteropDataSet;
import io.github.matrixlane.metadataregistry.datasetregistration.repository.InteropDataElementRepository;
import io.github.matrixlane.metadataregistry.datasetregistration.repository.InteropDataSetRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for importing interoperability dataset templates (互联互通数据集模板).
 *
 * <p>Supports Excel (.xlsx) and CSV formats. Parses headers using configurable
 * column mappings, validates rows, and persists data elements with incremental
 * update support.
 *
 * <p>Import strategy:
 * <ol>
 *   <li><b>Parse phase</b>: Read file, detect columns, validate rows, collect errors</li>
 *   <li><b>Transform phase</b>: Map interop fields to ISO 11179 concepts (MVP: hardcoded)</li>
 *   <li><b>Persist phase</b>: Batch upsert within a single transaction per dataset</li>
 * </ol>
 */
@Service
public class InteropDataSetImportService {

    private static final Logger log = LoggerFactory.getLogger(InteropDataSetImportService.class);

    /** Expected minimum fields for a valid data element row. */
    private static final int MIN_FIELDS_EXPECTED = 3;

    private final InteropDataSetRepository dataSetRepository;
    private final InteropDataElementRepository dataElementRepository;

    public InteropDataSetImportService(InteropDataSetRepository dataSetRepository,
                                       InteropDataElementRepository dataElementRepository) {
        this.dataSetRepository = dataSetRepository;
        this.dataElementRepository = dataElementRepository;
    }

    // ─────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────

    /**
     * Import interoperability dataset from an Excel (.xlsx) file.
     *
     * @param fileName    the original file name (used for reference)
     * @param inputStream the Excel file content
     * @return import result with statistics and per-row errors
     */
    @Transactional
    public ImportResult importFromExcel(String fileName, InputStream inputStream) {
        long startTime = System.currentTimeMillis();
        ImportResult result = new ImportResult();

        if (inputStream == null) {
            result.addError(new ImportError(0, "InputStream is null"));
            return result;
        }

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                result.addError(new ImportError(0, "Excel file contains no sheets"));
                return result;
            }

            Iterator<Row> rowIterator = sheet.iterator();
            if (!rowIterator.hasNext()) {
                result.addError(new ImportError(0, "Sheet is empty"));
                return result;
            }

            // Parse header row (row 0)
            Row headerRow = rowIterator.next();
            ColumnMappingConfig mapping = new ColumnMappingConfig();
            Map<Integer, String> columnIndexToField = mapColumns(headerRow, mapping, result);

            if (columnIndexToField.isEmpty()) {
                result.addError(new ImportError(1, "No recognizable columns found in header row"));
                return result;
            }

            // Detect dataset info from first data row
            InteropDataSet dataSet = resolveDataSet(fileName, headerRow, result);

            // Parse data rows
            int rowNum = 2; // 1-based, header is row 1
            List<InteropDataElement> batch = new ArrayList<>();

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                InteropDataElement element = parseRow(row, rowNum, columnIndexToField, result);

                if (element != null) {
                    element.setDataSet(dataSet);
                    batch.add(element);
                    result.setImportedRows(result.getImportedRows() + 1);
                }

                rowNum++;
            }

            // Persist batch
            if (!batch.isEmpty()) {
                persistBatch(dataSet, batch);
            }

            // Update dataset element count
            long count = dataElementRepository.countByDataSetId(dataSet.getId());
            dataSet.setElementCount((int) count);
            dataSetRepository.save(dataSet);

            result.setTotalRows(batch.size() + result.getErrorRows());
            result.markSuccess(dataSet.getId(), dataSet.getName());

        } catch (IOException e) {
            log.error("Failed to read Excel file: {}", fileName, e);
            result.addError(new ImportError(0, "File read error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error importing Excel file: {}", fileName, e);
            result.addError(new ImportError(0, "Unexpected error: " + e.getMessage()));
        }

        result.setDurationMs(System.currentTimeMillis() - startTime);
        return result;
    }

    /**
     * Import interoperability dataset from a CSV file.
     *
     * @param fileName    the original file name
     * @param inputStream the CSV file content
     * @return import result with statistics and per-row errors
     */
    @Transactional
    public ImportResult importFromCsv(String fileName, InputStream inputStream) {
        long startTime = System.currentTimeMillis();
        ImportResult result = new ImportResult();

        try (InputStreamReader reader = new InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8)) {
            // Read all lines
            List<String> lines = new ArrayList<>();
            java.io.BufferedReader bufferedReader = new java.io.BufferedReader(reader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                lines.add(line);
            }

            if (lines.isEmpty()) {
                result.addError(new ImportError(0, "CSV file is empty"));
                return result;
            }

            // Parse header — strip UTF-8 BOM if present
            String headerLine = lines.get(0);
            if (headerLine != null && !headerLine.isEmpty() && headerLine.charAt(0) == '\uFEFF') {
                headerLine = headerLine.substring(1);
            }
            String[] headers = parseCsvLine(headerLine);

            ColumnMappingConfig mapping = new ColumnMappingConfig();
            Map<Integer, String> columnIndexToField = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                String field = mapping.resolve(headers[i]);
                if (!headers[i].equals(field)) {
                    columnIndexToField.put(i, field);
                }
            }

            if (columnIndexToField.isEmpty()) {
                result.addError(new ImportError(1, "No recognizable columns found in header row"));
                return result;
            }

            // Resolve dataset
            InteropDataSet dataSet = createOrGetDataSet(fileName);

            // Parse data rows
            int rowNum = 2;
            List<InteropDataElement> batch = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                String[] values = parseCsvLine(lines.get(i));
                InteropDataElement element = parseRowFromValues(values, rowNum, columnIndexToField, result);

                if (element != null) {
                    element.setDataSet(dataSet);
                    batch.add(element);
                    result.setImportedRows(result.getImportedRows() + 1);
                }

                rowNum++;
            }

            // Persist batch
            if (!batch.isEmpty()) {
                persistBatch(dataSet, batch);
            }

            // Update dataset element count
            long count = dataElementRepository.countByDataSetId(dataSet.getId());
            dataSet.setElementCount((int) count);
            dataSetRepository.save(dataSet);

            result.setTotalRows(batch.size() + result.getErrorRows());
            result.markSuccess(dataSet.getId(), dataSet.getName());

        } catch (IOException e) {
            log.error("Failed to read CSV file: {}", fileName, e);
            result.addError(new ImportError(0, "File read error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error importing CSV file: {}", fileName, e);
            result.addError(new ImportError(0, "Unexpected error: " + e.getMessage()));
        }

        result.setDurationMs(System.currentTimeMillis() - startTime);
        return result;
    }

    // ─────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────

    /**
     * Map column header cells to internal field names.
     */
    private Map<Integer, String> mapColumns(Row headerRow, ColumnMappingConfig mapping, ImportResult result) {
        Map<Integer, String> columnIndexToField = new HashMap<>();

        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null) {
                continue;
            }
            String headerText = getCellValueAsString(cell);
            if (headerText == null || headerText.isBlank()) {
                continue;
            }

            String field = mapping.resolve(headerText);
            if (!headerText.equals(field)) {
                columnIndexToField.put(i, field);
            }
        }

        // Validate that at least 'deIdentifier' is found
        if (!columnIndexToField.containsValue("deIdentifier")) {
            log.warn("No 'deIdentifier' column mapping found; headers: {}", columnIndexToField);
        }

        return columnIndexToField;
    }

    /**
     * Resolve or create the dataset from the file name and header info.
     */
    private InteropDataSet resolveDataSet(String fileName, Row headerRow, ImportResult result) {
        // Try to extract dataset code from header row metadata
        String code = null;
        try {
            // Some templates have dataset code in the first header cell
            Cell firstCell = headerRow.getCell(0);
            if (firstCell != null) {
                String text = getCellValueAsString(firstCell);
                if (text != null && text.contains(".")) {
                    code = text.trim();
                }
            }
        } catch (Exception ignored) {
            // Header row may not contain dataset code
        }

        return createOrGetDataSet(fileName);
    }

    /**
     * Create a new dataset or reuse an existing one by file name derivation.
     * MVP: Simple strategy — derive dataset code from file name.
     */
    private InteropDataSet createOrGetDataSet(String fileName) {
        // Derive a dataset code from the file name (e.g., "HDSB01.01.xlsx" -> "HDSB01.01")
        String code = deriveCodeFromFileName(fileName);

        Optional<InteropDataSet> existing = dataSetRepository.findByCode(code);
        if (existing.isPresent()) {
            log.info("Reusing existing dataset: {}", code);
            return existing.get();
        }

        InteropDataSet dataSet = new InteropDataSet();
        dataSet.setCode(code);
        dataSet.setName(fileName.replaceAll("\\.(xlsx|csv)$", ""));
        dataSet.setSourceFileName(fileName);
        dataSet.setVersion("1.0");
        return dataSetRepository.save(dataSet);
    }

    /**
     * Derive a dataset code from the file name.
     * MVP: Simple heuristic — use the file base name.
     */
    private String deriveCodeFromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "UNKNOWN";
        }
        // Remove extension
        String baseName = fileName.replaceAll("\\.(xlsx|xls|csv)$", "");
        // Try to extract known pattern like HDSB01.01
        if (baseName.matches(".*[A-Z]{2,}[0-9]+\\.[0-9]+.*")) {
            java.util.regex.Matcher matcher =
                    java.util.regex.Pattern.compile("([A-Z]{2,}[0-9]+\\.[0-9]+)").matcher(baseName);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return baseName;
    }

    /**
     * Parse an Excel row into an InteropDataElement.
     *
     * @return the parsed element, or null if the row had errors
     */
    private InteropDataElement parseRow(Row row, int rowNum,
                                         Map<Integer, String> columnIndexToField,
                                         ImportResult result) {
        InteropDataElement element = new InteropDataElement();

        for (Map.Entry<Integer, String> entry : columnIndexToField.entrySet()) {
            int colIdx = entry.getKey();
            String field = entry.getValue();

            Cell cell = row.getCell(colIdx);
            String value = (cell != null) ? getCellValueAsString(cell) : null;

            if (value != null) {
                value = value.trim();
                if (value.isEmpty()) {
                    value = null;
                }
            }

            setField(element, field, value);
        }

        // Validate required fields
        if (element.getDeIdentifier() == null || element.getDeIdentifier().isBlank()) {
            result.addError(new ImportError(rowNum, "数据元标识符 (deIdentifier) is required",
                    getRowSummary(row)));
            return null;
        }
        if (element.getDeName() == null || element.getDeName().isBlank()) {
            result.addError(new ImportError(rowNum, 1,
                    "数据元名称 (deName) is required for identifier: " + element.getDeIdentifier(),
                    element.getDeIdentifier()));
            return null;
        }

        return element;
    }

    /**
     * Parse a CSV row values array into an InteropDataElement.
     */
    private InteropDataElement parseRowFromValues(String[] values, int rowNum,
                                                   Map<Integer, String> columnIndexToField,
                                                   ImportResult result) {
        InteropDataElement element = new InteropDataElement();

        for (Map.Entry<Integer, String> entry : columnIndexToField.entrySet()) {
            int colIdx = entry.getKey();
            String field = entry.getValue();

            String value = (colIdx < values.length) ? values[colIdx] : null;
            if (value != null) {
                value = value.trim();
                if (value.isEmpty()) {
                    value = null;
                }
            }

            setField(element, field, value);
        }

        // Validate required fields
        if (element.getDeIdentifier() == null || element.getDeIdentifier().isBlank()) {
            String rowSummary = (values.length > 0) ? values[0] : "(empty row)";
            result.addError(new ImportError(rowNum,
                    "数据元标识符 (deIdentifier) is required", rowSummary));
            return null;
        }
        if (element.getDeName() == null || element.getDeName().isBlank()) {
            result.addError(new ImportError(rowNum, 1,
                    "数据元名称 (deName) is required for identifier: " + element.getDeIdentifier(),
                    element.getDeIdentifier()));
            return null;
        }

        return element;
    }

    /**
     * Set a field on InteropDataElement by field name.
     */
    private void setField(InteropDataElement element, String field, String value) {
        if (value == null) {
            return;
        }

        switch (field) {
            case "deIdentifier" -> element.setDeIdentifier(value);
            case "deName" -> element.setDeName(value);
            case "deDefinition" -> element.setDeDefinition(value);
            case "dataType" -> element.setDataType(value);
            case "representationFormat" -> element.setRepresentationFormat(value);
            case "permissibleValues" -> element.setPermissibleValues(value);
            case "required" -> {
                if ("是".equals(value) || "Y".equalsIgnoreCase(value)
                        || "Yes".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value)
                        || "1".equals(value)) {
                    element.setRequired(true);
                } else if ("否".equals(value) || "N".equalsIgnoreCase(value)
                        || "No".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)
                        || "0".equals(value)) {
                    element.setRequired(false);
                }
                // else keep null (unknown)
            }
            default -> log.debug("Unknown field name: {} (value: {})", field, value);
        }
    }

    /**
     * Persist a batch of data elements using incremental update strategy.
     * If a data element with the same (dataSet, deIdentifier) exists, update it;
     * otherwise insert a new one.
     */
    private void persistBatch(InteropDataSet dataSet, List<InteropDataElement> elements) {
        for (InteropDataElement element : elements) {
            Optional<InteropDataElement> existing =
                    dataElementRepository.findByDataSetIdAndDeIdentifier(
                            dataSet.getId(), element.getDeIdentifier());

            if (existing.isPresent()) {
                // Incremental update: field-level overwrite
                InteropDataElement target = existing.get();
                mergeFields(target, element);
                target.setUpdatedAt(LocalDateTime.now());
                dataElementRepository.save(target);
            } else {
                element.setMappingStatus(InteropDataElement.MappingStatus.UNMAPPED);
                dataElementRepository.save(element);
            }
        }
    }

    /**
     * Merge fields from source into target (non-null source fields overwrite target).
     * Follows the trio review decision: field-level overwrite for incremental updates.
     */
    private void mergeFields(InteropDataElement target, InteropDataElement source) {
        if (source.getDeName() != null) target.setDeName(source.getDeName());
        if (source.getDeDefinition() != null) target.setDeDefinition(source.getDeDefinition());
        if (source.getDataType() != null) target.setDataType(source.getDataType());
        if (source.getRepresentationFormat() != null) target.setRepresentationFormat(source.getRepresentationFormat());
        if (source.getPermissibleValues() != null) target.setPermissibleValues(source.getPermissibleValues());
        if (source.getRequired() != null) target.setRequired(source.getRequired());
    }

    /**
     * Extract cell value as string, handling various cell types.
     */
    private static String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                // Check if it's an integer value
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    try {
                        yield cell.getStringCellValue();
                    } catch (Exception e2) {
                        yield cell.getCellFormula();
                    }
                }
            }
            case BLANK -> null;
            default -> null;
        };
    }

    /**
     * Parse a single CSV line, handling quoted values with embedded commas.
     * Simple implementation: splits on commas but respects double-quote escaping.
     */
    private static String[] parseCsvLine(String line) {
        if (line == null || line.isBlank()) {
            return new String[0];
        }

        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);

            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++; // skip escaped quote
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(ch);
                }
            } else {
                if (ch == '"') {
                    inQuotes = true;
                } else if (ch == ',') {
                    fields.add(current.toString().trim());
                    current = new StringBuilder();
                } else {
                    current.append(ch);
                }
            }
        }

        // Add the last field
        fields.add(current.toString().trim());

        return fields.toArray(new String[0]);
    }

    /**
     * Get a summary of the row values for error reporting.
     */
    private static String getRowSummary(Row row) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, row.getLastCellNum()); i++) {
            Cell cell = row.getCell(i);
            if (cell != null) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(getCellValueAsString(cell));
            }
        }
        return sb.toString();
    }
}
