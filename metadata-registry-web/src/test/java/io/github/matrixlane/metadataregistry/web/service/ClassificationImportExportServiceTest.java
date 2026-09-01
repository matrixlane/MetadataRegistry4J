package io.github.matrixlane.metadataregistry.web.service;

import com.opencsv.CSVWriter;
import io.github.matrixlane.metadataregistry.ext.classification.ClassificationNode;
import io.github.matrixlane.metadataregistry.ext.classification.ClassificationScheme;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.classification.ClassificationImportResult;
import io.github.matrixlane.metadataregistry.web.dto.classification.ClassificationImportRow;
import io.github.matrixlane.metadataregistry.web.dto.classification.ImportErrorDetail;
import io.github.matrixlane.metadataregistry.web.repository.ClassificationNodeRepository;
import io.github.matrixlane.metadataregistry.web.repository.ClassificationSchemeRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassificationImportExportServiceTest {

    @Mock
    private ClassificationSchemeRepository schemeRepository;

    @Mock
    private ClassificationNodeRepository nodeRepository;

    @InjectMocks
    private ClassificationImportExportService service;

    private UUID schemeId;
    private ClassificationScheme scheme;

    @BeforeEach
    void setUp() {
        schemeId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        scheme = new ClassificationScheme("Test Scheme");
        scheme.setId(schemeId);
        scheme.setDescription("Test Desc");
        scheme.setVersion("1.0");
        scheme.setStatus("DRAFT");
    }

    // ==================== File Parsing Tests ====================

    @Test
    void parseFile_shouldParseCsvSuccessfully() throws IOException {
        String csv = buildCsv(new String[][]{
                {"scheme_name", "scheme_description", "scheme_version", "scheme_status",
                        "node_code", "node_name", "node_description", "node_definition",
                        "parent_code", "sort_order"},
                {"Scheme1", "Desc", "1.0", "DRAFT", "N01", "Node1", "", "", "", "0"},
                {"Scheme1", "Desc", "1.0", "DRAFT", "N02", "Node2", "", "", "N01", "1"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        List<ClassificationImportRow> rows = service.parseFile(file);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).nodeCode()).isEqualTo("N01");
        assertThat(rows.get(1).parentCode()).isEqualTo("N01");
    }

    @Test
    void parseFile_shouldParseExcelSuccessfully() throws IOException {
        byte[] excelBytes = buildExcel(new String[][]{
                {"scheme_name", "scheme_description", "scheme_version", "scheme_status",
                        "node_code", "node_name", "node_description", "node_definition",
                        "parent_code", "sort_order"},
                {"Scheme1", "Desc", "1.0", "DRAFT", "N01", "Node1", "", "", "", "0"},
                {"Scheme1", "Desc", "1.0", "DRAFT", "N02", "Node2", "", "", "N01", "1"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        List<ClassificationImportRow> rows = service.parseFile(file);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).nodeCode()).isEqualTo("N01");
        assertThat(rows.get(1).parentCode()).isEqualTo("N01");
    }

    @Test
    void parseFile_shouldRejectEmptyFile() {
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", new byte[0]);

        assertThatThrownBy(() -> service.parseFile(file))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ApiCodes.BAD_REQUEST));
    }

    @Test
    void parseFile_shouldRejectOversizedFile() {
        byte[] largeData = new byte[11 * 1024 * 1024]; // 11MB
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", largeData);

        assertThatThrownBy(() -> service.parseFile(file))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ApiCodes.BAD_REQUEST))
                .hasMessageContaining("10MB");
    }

    @Test
    void parseFile_shouldRejectUnsupportedFormat() {
        MultipartFile file = new MockMultipartFile("file", "test.txt",
                "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.parseFile(file))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ApiCodes.BAD_REQUEST))
                .hasMessageContaining("不支持");
    }

    @Test
    void parseFile_shouldSkipEmptyRowsInCsv() throws IOException {
        String csv = buildCsv(new String[][]{
                {"scheme_name", "scheme_description", "scheme_version", "scheme_status",
                        "node_code", "node_name", "node_description", "node_definition",
                        "parent_code", "sort_order"},
                {"Scheme1", "", "", "", "N01", "Node1", "", "", "", ""},
                {"", "", "", "", "", "", "", "", "", ""},  // empty row
                {"Scheme1", "", "", "", "N02", "Node2", "", "", "", ""}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        List<ClassificationImportRow> rows = service.parseFile(file);

        assertThat(rows).hasSize(2);
    }

    @Test
    void parseFile_shouldHandleCsvWithoutOptionalColumns() throws IOException {
        String csv = buildCsv(new String[][]{
                {"node_code", "node_name"},
                {"N01", "Node1"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        List<ClassificationImportRow> rows = service.parseFile(file);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).nodeCode()).isEqualTo("N01");
        assertThat(rows.get(0).nodeName()).isEqualTo("Node1");
        assertThat(rows.get(0).schemeName()).isNull();
    }

    // ==================== Prevalidation Tests ====================

    @Test
    void prevalidateImport_shouldReturnSuccessForValidData() throws IOException {
        String csv = buildCsv(new String[][]{
                buildHeader(),
                {"Scheme1", "", "", "", "N01", "Node1", "", "", "", "0"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ClassificationImportResult result = service.prevalidateImport(file, null);

        assertThat(result.success()).isTrue();
        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.errorCount()).isEqualTo(0);
    }

    @Test
    void prevalidateImport_shouldDetectEmptyFile() throws IOException {
        String csv = buildCsv(new String[][]{buildHeader()});
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ClassificationImportResult result = service.prevalidateImport(file, null);

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).errorType()).isEqualTo("EMPTY_FILE");
    }

    @Test
    void prevalidateImport_shouldDetectMissingSchemeWhenNoTarget() throws IOException {
        String csv = buildCsv(new String[][]{
                {"node_code", "node_name"},
                {"N01", "Node1"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ClassificationImportResult result = service.prevalidateImport(file, null);

        assertThat(result.success()).isFalse();
        assertThat(result.errors().get(0).errorType()).isEqualTo("MISSING_SCHEME");
    }

    @Test
    void prevalidateImport_shouldDetectDuplicateNodeCodes() throws IOException {
        String csv = buildCsv(new String[][]{
                buildHeader(),
                {"Scheme1", "", "", "", "N01", "Node1", "", "", "", "0"},
                {"Scheme1", "", "", "", "N01", "Node2", "", "", "", "1"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ClassificationImportResult result = service.prevalidateImport(file, null);

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors().get(0).errorType()).isEqualTo("DUPLICATE_CODE");
    }

    @Test
    void prevalidateImport_shouldDetectDuplicateNodeNamesUnderSameParent() throws IOException {
        String csv = buildCsv(new String[][]{
                buildHeader(),
                {"Scheme1", "", "", "", "N01", "Node1", "", "", "", "0"},
                {"Scheme1", "", "", "", "N02", "Node1", "", "", "", "1"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ClassificationImportResult result = service.prevalidateImport(file, null);

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors().get(0).errorType()).isEqualTo("DUPLICATE_NAME");
    }

    @Test
    void prevalidateImport_shouldDetectMissingNodeCode() throws IOException {
        String csv = buildCsv(new String[][]{
                buildHeader(),
                {"Scheme1", "", "", "", "", "Node1", "", "", "", "0"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ClassificationImportResult result = service.prevalidateImport(file, null);

        assertThat(result.success()).isFalse();
        assertThat(result.errors().get(0).errorType()).isEqualTo("REQUIRED");
        assertThat(result.errors().get(0).columnName()).isEqualTo("node_code");
    }

    @Test
    void prevalidateImport_shouldDetectMissingNodeName() throws IOException {
        String csv = buildCsv(new String[][]{
                buildHeader(),
                {"Scheme1", "", "", "", "N01", "", "", "", "", "0"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ClassificationImportResult result = service.prevalidateImport(file, null);

        assertThat(result.success()).isFalse();
        assertThat(result.errors().get(0).errorType()).isEqualTo("REQUIRED");
        assertThat(result.errors().get(0).columnName()).isEqualTo("node_name");
    }

    @Test
    void prevalidateImport_shouldDetectInvalidNodeCodeFormat() throws IOException {
        String csv = buildCsv(new String[][]{
                buildHeader(),
                {"Scheme1", "", "", "", "N@01", "Node1", "", "", "", "0"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ClassificationImportResult result = service.prevalidateImport(file, null);

        assertThat(result.success()).isFalse();
        assertThat(result.errors().get(0).errorType()).isEqualTo("INVALID_FORMAT");
    }

    @Test
    void prevalidateImport_shouldDetectTooLongNodeCode() throws IOException {
        String longCode = "a".repeat(256);
        String csv = buildCsv(new String[][]{
                buildHeader(),
                {"Scheme1", "", "", "", longCode, "Node1", "", "", "", "0"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ClassificationImportResult result = service.prevalidateImport(file, null);

        assertThat(result.success()).isFalse();
        assertThat(result.errors().get(0).errorType()).isEqualTo("TOO_LONG");
    }

    @Test
    void prevalidateImport_shouldDetectTooLongNodeName() throws IOException {
        String longName = "a".repeat(256);
        String csv = buildCsv(new String[][]{
                buildHeader(),
                {"Scheme1", "", "", "", "N01", longName, "", "", "", "0"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ClassificationImportResult result = service.prevalidateImport(file, null);

        assertThat(result.success()).isFalse();
        assertThat(result.errors().get(0).errorType()).isEqualTo("TOO_LONG");
    }

    @Test
    void prevalidateImport_shouldDetectTooLongDescription() throws IOException {
        String longDesc = "a".repeat(4001);
        String csv = buildCsv(new String[][]{
                buildHeader(),
                {"Scheme1", "", "", "", "N01", "Node1", longDesc, "", "", "0"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ClassificationImportResult result = service.prevalidateImport(file, null);

        assertThat(result.success()).isFalse();
        assertThat(result.errors().get(0).errorType()).isEqualTo("TOO_LONG");
    }

    @Test
    void prevalidateImport_shouldDetectTooLongDefinition() throws IOException {
        String longDef = "a".repeat(4001);
        String csv = buildCsv(new String[][]{
                buildHeader(),
                {"Scheme1", "", "", "", "N01", "Node1", "", longDef, "", "0"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ClassificationImportResult result = service.prevalidateImport(file, null);

        assertThat(result.success()).isFalse();
        assertThat(result.errors().get(0).errorType()).isEqualTo("TOO_LONG");
    }

    @Test
    void prevalidateImport_shouldDetectHierarchyBreak() throws IOException {
        String csv = buildCsv(new String[][]{
                buildHeader(),
                {"Scheme1", "", "", "", "N01", "Node1", "", "", "NONEXIST", "0"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ClassificationImportResult result = service.prevalidateImport(file, null);

        assertThat(result.success()).isFalse();
        assertThat(result.errors().get(0).errorType()).isEqualTo("PARENT_NOT_FOUND");
    }

    @Test
    void prevalidateImport_shouldAcceptExistingParentInDb() throws IOException {
        String csv = buildCsv(new String[][]{
                buildHeader(),
                {"Scheme1", "", "", "", "N01", "Node1", "", "", "EXISTING", "0"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        when(nodeRepository.existsByClassificationSchemeIdAndNodeCode(schemeId, "EXISTING"))
                .thenReturn(true);

        ClassificationImportResult result = service.prevalidateImport(file, schemeId);

        assertThat(result.success()).isTrue();
    }

    @Test
    void prevalidateImport_shouldDetectNegativeSortOrder() throws IOException {
        String csv = buildCsv(new String[][]{
                buildHeader(),
                {"Scheme1", "", "", "", "N01", "Node1", "", "", "", "-1"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ClassificationImportResult result = service.prevalidateImport(file, null);

        assertThat(result.success()).isFalse();
        assertThat(result.errors().get(0).errorType()).isEqualTo("INVALID_VALUE");
    }

    @Test
    void prevalidateImport_shouldDetectCircularReference() throws IOException {
        String csv = buildCsv(new String[][]{
                buildHeader(),
                {"Scheme1", "", "", "", "N01", "Node1", "", "", "N02", "0"},
                {"Scheme1", "", "", "", "N02", "Node2", "", "", "N01", "1"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ClassificationImportResult result = service.prevalidateImport(file, null);

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.errorType().equals("CIRCULAR_REFERENCE"));
    }

    @Test
    void prevalidateImport_shouldDetectSelfReferencingCycle() throws IOException {
        String csv = buildCsv(new String[][]{
                buildHeader(),
                {"Scheme1", "", "", "", "N01", "Node1", "", "", "N01", "0"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ClassificationImportResult result = service.prevalidateImport(file, null);

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.errorType().equals("CIRCULAR_REFERENCE"));
    }

    // ==================== Import Tests ====================

    @Test
    void importFromFile_shouldCreateSchemeAndNodes() throws IOException {
        String csv = buildCsv(new String[][]{
                buildHeader(),
                {"NewScheme", "NewDesc", "1.0", "DRAFT", "N01", "Node1", "", "", "", "0"},
                {"NewScheme", "NewDesc", "1.0", "DRAFT", "N02", "Node2", "", "", "N01", "1"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        when(schemeRepository.findAll()).thenReturn(Collections.emptyList());
        when(schemeRepository.save(any(ClassificationScheme.class))).thenAnswer(i -> {
            ClassificationScheme s = i.getArgument(0);
            s.setId(schemeId);
            return s;
        });
        when(nodeRepository.save(any(ClassificationNode.class))).thenAnswer(i -> {
            ClassificationNode n = i.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });

        ClassificationImportResult result = service.importFromFile(file, null);

        assertThat(result.success()).isTrue();
        assertThat(result.importedNodes()).isEqualTo(2);
        assertThat(result.schemeName()).isEqualTo("NewScheme");
        verify(schemeRepository).save(any(ClassificationScheme.class));
        verify(nodeRepository, times(2)).save(any(ClassificationNode.class));
    }

    @Test
    void importFromFile_shouldReuseExistingScheme() throws IOException {
        String csv = buildCsv(new String[][]{
                buildHeader(),
                {"Test Scheme", "", "", "", "N01", "Node1", "", "", "", "0"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        when(schemeRepository.findAll()).thenReturn(List.of(scheme));
        when(nodeRepository.save(any(ClassificationNode.class))).thenAnswer(i -> {
            ClassificationNode n = i.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });

        ClassificationImportResult result = service.importFromFile(file, null);

        assertThat(result.success()).isTrue();
        assertThat(result.schemeId()).isEqualTo(schemeId);
        verify(schemeRepository, never()).save(any(ClassificationScheme.class));
    }

    @Test
    void importFromFile_shouldImportToTargetScheme() throws IOException {
        String csv = buildCsv(new String[][]{
                buildHeader(),
                {"", "", "", "", "N01", "Node1", "", "", "", "0"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(nodeRepository.save(any(ClassificationNode.class))).thenAnswer(i -> {
            ClassificationNode n = i.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });

        ClassificationImportResult result = service.importFromFile(file, schemeId);

        assertThat(result.success()).isTrue();
        assertThat(result.schemeId()).isEqualTo(schemeId);
    }

    @Test
    void importFromFile_shouldReturnErrorsWhenValidationFails() throws IOException {
        String csv = buildCsv(new String[][]{
                buildHeader(),
                {"Scheme1", "", "", "", "", "Node1", "", "", "", "0"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ClassificationImportResult result = service.importFromFile(file, null);

        assertThat(result.success()).isFalse();
        assertThat(result.importedNodes()).isEqualTo(0);
        assertThat(result.errorCount()).isGreaterThan(0);
    }

    @Test
    void importFromFile_shouldDetectExistingNodeCodeInDb() throws IOException {
        String csv = buildCsv(new String[][]{
                buildHeader(),
                {"", "", "", "", "EXIST", "Node1", "", "", "", "0"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(nodeRepository.existsByClassificationSchemeIdAndNodeCode(schemeId, "EXIST"))
                .thenReturn(true);

        ClassificationImportResult result = service.importFromFile(file, schemeId);

        assertThat(result.success()).isFalse();
        assertThat(result.errors().get(0).errorType()).isEqualTo("ALREADY_EXISTS");
    }

    @Test
    void importFromFile_shouldBuildHierarchyCorrectly() throws IOException {
        String csv = buildCsv(new String[][]{
                buildHeader(),
                {"Scheme1", "", "", "", "CHILD", "Child", "", "", "PARENT", "0"},
                {"Scheme1", "", "", "", "PARENT", "Parent", "", "", "", "0"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        when(schemeRepository.findAll()).thenReturn(Collections.emptyList());
        when(schemeRepository.save(any(ClassificationScheme.class))).thenAnswer(i -> {
            ClassificationScheme s = i.getArgument(0);
            s.setId(schemeId);
            return s;
        });

        ClassificationNode savedParent = new ClassificationNode("PARENT", "Parent", scheme);
        savedParent.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        when(nodeRepository.save(any(ClassificationNode.class))).thenAnswer(i -> {
            ClassificationNode n = i.getArgument(0);
            if (n.getNodeCode().equals("PARENT") && n.getId() == null) {
                n.setId(savedParent.getId());
            } else if (n.getId() == null) {
                n.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
            }
            return n;
        });

        ClassificationImportResult result = service.importFromFile(file, null);

        assertThat(result.success()).isTrue();
        assertThat(result.importedNodes()).isEqualTo(2);
    }

    @Test
    void importFromFile_shouldThrowWhenTargetSchemeNotFound() throws IOException {
        String csv = buildCsv(new String[][]{
                buildHeader(),
                {"", "", "", "", "N01", "Node1", "", "", "", "0"}
        });
        MultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        when(schemeRepository.findById(schemeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.importFromFile(file, schemeId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ApiCodes.NOT_FOUND));
    }

    // ==================== Export Tests ====================

    @Test
    void exportToCsv_shouldReturnValidCsv() {
        ClassificationNode node1 = new ClassificationNode("N01", "Node1", scheme);
        node1.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        node1.setDescription("Desc1");
        node1.setDefinition("Def1");
        node1.setSortOrder(0);

        ClassificationNode node2 = new ClassificationNode("N02", "Node2", scheme);
        node2.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        node2.setParent(node1);
        node2.setSortOrder(1);

        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(nodeRepository.findByClassificationSchemeIdOrderBySortOrderAsc(schemeId))
                .thenReturn(List.of(node1, node2));

        byte[] data = service.exportToCsv(schemeId);
        String csv = new String(data, StandardCharsets.UTF_8);

        assertThat(csv).contains("scheme_name,scheme_description");
        assertThat(csv).contains("N01,Node1");
        assertThat(csv).contains("N02,Node2");
        assertThat(csv).contains("N01"); // parent_code
    }

    @Test
    void exportToExcel_shouldReturnValidXlsx() throws IOException {
        ClassificationNode node1 = new ClassificationNode("N01", "Node1", scheme);
        node1.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        node1.setSortOrder(0);

        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(nodeRepository.findByClassificationSchemeIdOrderBySortOrderAsc(schemeId))
                .thenReturn(List.of(node1));

        byte[] data = service.exportToExcel(schemeId);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isGreaterThanOrEqualTo(2);
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("scheme_name");
            assertThat(header.getCell(4).getStringCellValue()).isEqualTo("node_code");
            Row dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(4).getStringCellValue()).isEqualTo("N01");
        }
    }

    @Test
    void exportToCsv_shouldThrowWhenSchemeNotFound() {
        when(schemeRepository.findById(schemeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exportToCsv(schemeId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ApiCodes.NOT_FOUND));
    }

    @Test
    void exportToExcel_shouldThrowWhenSchemeNotFound() {
        when(schemeRepository.findById(schemeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exportToExcel(schemeId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ApiCodes.NOT_FOUND));
    }

    // ==================== Topological Sort Tests ====================

    @Test
    void topologicalSort_shouldOrderParentsBeforeChildren() {
        List<ClassificationImportRow> rows = new ArrayList<>();
        rows.add(new ClassificationImportRow("S", "", "", "", "C3", "Child3", "", "", "C2", 0));
        rows.add(new ClassificationImportRow("S", "", "", "", "C1", "Child1", "", "", "P", 0));
        rows.add(new ClassificationImportRow("S", "", "", "", "P", "Parent", "", "", "", 0));
        rows.add(new ClassificationImportRow("S", "", "", "", "C2", "Child2", "", "", "P", 0));

        List<ClassificationImportRow> sorted = service.topologicalSort(rows);

        assertThat(sorted).hasSize(4);
        assertThat(sorted.get(0).nodeCode()).isEqualTo("P");
        // C1, C2, C3 can be in any order as long as P is first and C2 is before C3
        int pIdx = findIndex(sorted, "P");
        int c1Idx = findIndex(sorted, "C1");
        int c2Idx = findIndex(sorted, "C2");
        int c3Idx = findIndex(sorted, "C3");
        assertThat(pIdx).isLessThan(c1Idx);
        assertThat(pIdx).isLessThan(c2Idx);
        assertThat(c2Idx).isLessThan(c3Idx);
    }

    @Test
    void topologicalSort_shouldHandleRootNodesOnly() {
        List<ClassificationImportRow> rows = List.of(
                new ClassificationImportRow("S", "", "", "", "A", "A", "", "", "", 0),
                new ClassificationImportRow("S", "", "", "", "B", "B", "", "", "", 0)
        );

        List<ClassificationImportRow> sorted = service.topologicalSort(rows);

        assertThat(sorted).hasSize(2);
    }

    @Test
    void topologicalSort_shouldHandleExternalParent() {
        // Parent not in file (will be in DB)
        List<ClassificationImportRow> rows = List.of(
                new ClassificationImportRow("S", "", "", "", "C1", "Child1", "", "", "EXT", 0)
        );

        List<ClassificationImportRow> sorted = service.topologicalSort(rows);

        assertThat(sorted).hasSize(1);
        assertThat(sorted.get(0).nodeCode()).isEqualTo("C1");
    }

    // ==================== Helper Methods ====================

    private String[] buildHeader() {
        return new String[]{
                "scheme_name", "scheme_description", "scheme_version", "scheme_status",
                "node_code", "node_name", "node_description", "node_definition",
                "parent_code", "sort_order"
        };
    }

    private String buildCsv(String[][] rows) {
        StringWriter writer = new StringWriter();
        try (CSVWriter csvWriter = new CSVWriter(writer)) {
            for (String[] row : rows) {
                csvWriter.writeNext(row);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return writer.toString();
    }

    private byte[] buildExcel(String[][] data) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            for (int i = 0; i < data.length; i++) {
                Row row = sheet.createRow(i);
                for (int j = 0; j < data[i].length; j++) {
                    row.createCell(j).setCellValue(data[i][j]);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private int findIndex(List<ClassificationImportRow> rows, String code) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).nodeCode().equals(code)) {
                return i;
            }
        }
        return -1;
    }
}
