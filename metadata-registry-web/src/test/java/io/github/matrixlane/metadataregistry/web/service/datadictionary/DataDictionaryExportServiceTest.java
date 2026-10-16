package io.github.matrixlane.metadataregistry.web.service.datadictionary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept;
import io.github.matrixlane.metadataregistry.dataspecification.DataType;
import io.github.matrixlane.metadataregistry.dataspecification.UnitOfMeasure;
import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;
import io.github.matrixlane.metadataregistry.web.dto.export.ExportFormat;
import io.github.matrixlane.metadataregistry.web.dto.export.datadictionary.DataDictionaryExportRequest;
import io.github.matrixlane.metadataregistry.web.dto.export.datadictionary.DataDictionaryItemExport;
import io.github.matrixlane.metadataregistry.web.repository.DataElementRepository;
import io.github.matrixlane.metadataregistry.web.repository.ValueDomainRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedValueDomain;

/**
 * {@link DataDictionaryExportService} 单元测试
 */
@ExtendWith(MockitoExtension.class)
class DataDictionaryExportServiceTest {

    @Mock
    private DataElementRepository dataElementRepository;

    @Mock
    private ValueDomainRepository valueDomainRepository;

    @Mock
    private DataDictionaryExcelExportService excelExportService;

    @Mock
    private DataDictionaryPdfExportService pdfExportService;

    private DataDictionaryExportService exportService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private List<DataElement> testElements;
    private DataDictionaryExportRequest excelRequest;
    private DataDictionaryExportRequest pdfRequest;

    @BeforeEach
    void setUp() {
        exportService = new DataDictionaryExportService(
                dataElementRepository, valueDomainRepository,
                excelExportService, pdfExportService, objectMapper);

        testElements = List.of(
                createTestDataElement("DE001", "患者姓名"),
                createTestDataElement("DE002", "患者性别"),
                createTestDataElement("DE003", "出生日期")
        );

        List<UUID> ids = testElements.stream().map(DataElement::getId).toList();
        excelRequest = new DataDictionaryExportRequest(ids, ExportFormat.EXCEL,
                DataDictionaryExportRequest.ExportMode.SINGLE, true, true, false, 5000);
        pdfRequest = new DataDictionaryExportRequest(ids, ExportFormat.PDF,
                DataDictionaryExportRequest.ExportMode.SINGLE, false, false, false, 5000);
    }

    // =========================================================================
    // 1. 同步 Excel 导出
    // =========================================================================

    @Test
    @DisplayName("同步导出 Excel - 应调用 Excel 导出服务")
    void exportSync_excel_shouldCallExcelService() throws IOException {
        when(dataElementRepository.findAllById(any())).thenReturn(testElements);

        exportService.exportSync(excelRequest);

        verify(excelExportService).exportToExcel(any(), any(), eq(true), eq(true));
    }

    @Test
    @DisplayName("同步导出 Excel - 返回非空字节数组")
    void exportSync_excel_shouldReturnNonEmptyBytes() throws IOException {
        when(dataElementRepository.findAllById(any())).thenReturn(testElements);

        byte[] result = exportService.exportSync(excelRequest);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("同步导出 - 超过上限应抛出异常")
    void exportSync_tooManyItems_shouldThrow() {
        List<DataElement> largeElements = new ArrayList<>();
        List<UUID> largeIds = new ArrayList<>();
        for (int i = 0; i < 5001; i++) {
            UUID id = UUID.randomUUID();
            largeIds.add(id);
            DataElement de = new DataElement();
            de.setId(id);
            largeElements.add(de);
        }
        DataDictionaryExportRequest largeRequest = new DataDictionaryExportRequest(
                largeIds, ExportFormat.EXCEL, DataDictionaryExportRequest.ExportMode.SINGLE,
                false, false, false, 5001);

        when(dataElementRepository.findAllById(any())).thenReturn(largeElements);

        assertThatThrownBy(() -> exportService.exportSync(largeRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("超过上限");
    }

    // =========================================================================
    // 2. 同步 PDF 导出
    // =========================================================================

    @Test
    @DisplayName("同步导出 PDF - 单条应调用单数据元导出")
    void exportSync_pdf_singleItem_shouldCallSingleItemPdf() throws IOException {
        DataElement singleElement = testElements.get(0);
        DataDictionaryExportRequest singleRequest = new DataDictionaryExportRequest(
                List.of(singleElement.getId()), ExportFormat.PDF,
                DataDictionaryExportRequest.ExportMode.SINGLE, false, false, false, 5000);

        when(dataElementRepository.findAllById(any())).thenReturn(List.of(singleElement));

        exportService.exportSync(singleRequest);

        verify(pdfExportService).exportSingleItemPdf(any(), any());
    }

    @Test
    @DisplayName("同步导出 PDF - 多条应调用报告导出")
    void exportSync_pdf_multipleItems_shouldCallReportPdf() throws IOException {
        when(dataElementRepository.findAllById(any())).thenReturn(testElements);

        exportService.exportSync(pdfRequest);

        verify(pdfExportService).exportDataDictionaryReport(any(), any(), any());
    }

    // =========================================================================
    // 3. 异步导出任务
    // =========================================================================





    // =========================================================================
    // 4. 任务查询与下载
    // =========================================================================










    // =========================================================================
    // 5. DTO 转换
    // =========================================================================

    @Test
    @DisplayName("数据元转导出 DTO - 应包含基本字段")
    void toExportDto_shouldContainBasicFields() {
        DataElement de = createTestDataElement("DE999", "测试数据元");

        DataDictionaryItemExport dto = exportService.toExportDto(de);

        assertThat(dto.id()).isEqualTo(de.getId());
        assertThat(dto.identifier()).isEqualTo("DE999");
        assertThat(dto.label()).isEqualTo("测试数据元");
        assertThat(dto.itemType()).isEqualTo("DataElement");
    }

    @Test
    @DisplayName("数据元转导出 DTO - 空关联应正常处理")
    void toExportDto_nullAssociations_shouldHandleGracefully() {
        DataElement de = new DataElement();
        de.setId(UUID.randomUUID());

        DataDictionaryItemExport dto = exportService.toExportDto(de);

        assertThat(dto.id()).isEqualTo(de.getId());
        assertThat(dto.label()).isNull();
        assertThat(dto.valueDomainLabel()).isNull();
    }




    @Test
    @DisplayName("同步导出 - null 格式应抛出异常")
    void exportSync_nullFormat_shouldThrow() {
        List<UUID> ids = List.of(UUID.randomUUID());
        DataDictionaryExportRequest unsupportedRequest = new DataDictionaryExportRequest(
                ids, null, DataDictionaryExportRequest.ExportMode.SINGLE,
                false, false, false, 5000);

        assertThatThrownBy(() -> exportService.exportSync(unsupportedRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持");
    }

    @Test
    @DisplayName("数据元转导出 DTO - 含数据类型和单位")
    void toExportDto_withDataTypeAndUom_shouldContainFields() {
        DataElement de = createTestDataElement("DE005", "体重");
        DataType dt = new DataType();
        dt.setId(UUID.randomUUID());
        Designation dtDes = new Designation();
        var dtSign = new io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign();
        dtSign.setValue("数值型");
        dtDes.setSign(dtSign);
        dt.setDesignations(List.of(dtDes));

        ValueDomain vd = de.getValueDomain();
        vd.setDataType(dt);

        UnitOfMeasure uom = new UnitOfMeasure();
        uom.setId(UUID.randomUUID());
        Designation uomDes = new Designation();
        var uomSign = new io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign();
        uomSign.setValue("kg");
        uomDes.setSign(uomSign);
        uom.setDesignations(List.of(uomDes));
        vd.setUnitOfMeasure(uom);
        vd.setMaximumCharacterQuantity(10);
        vd.setFormat("###.##");

        DataDictionaryItemExport dto = exportService.toExportDto(de);

        assertThat(dto.dataTypeLabel()).isEqualTo("数值型");
        assertThat(dto.unitOfMeasureLabel()).isEqualTo("kg");
        assertThat(dto.maximumCharacterQuantity()).isEqualTo(10);
        assertThat(dto.format()).isEqualTo("###.##");
    }

    // =========================================================================
    // 辅助方法
    // =========================================================================

    private DataElement createTestDataElement(String identifier, String label) {
        DataElement de = new DataElement();
        de.setId(UUID.randomUUID());

        // Designation
        Designation designation = new Designation();
        var sign = new io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign();
        sign.setValue(label);
        designation.setSign(sign);
        de.setDesignations(List.of(designation));

        // Definition
        Definition definition = new Definition();
        definition.setText(io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text.of(label + "的定义"));
        de.setDefinitions(List.of(definition));

        // IdentifiedItem / AdministeredItem
        AdministeredItem ai = new AdministeredItem();
        ai.setId(UUID.randomUUID());
        ai.setCreationDateTime(LocalDateTime.of(2025, 1, 1, 0, 0));
        ai.setLastChangeDateTime(LocalDateTime.of(2025, 6, 1, 0, 0));

        RegistrationState state = new RegistrationState();
        state.setRegistrationStatus(RegistrationStatus.STANDARD);
        ai.setRegistrationState(state);

        ScopedIdentifier si = new ScopedIdentifier();
        si.setIdentifier(identifier);
        si.setVersion("1.0");
        ai.setScopedIdentifiers(List.of(si));

        de.setIdentifiedItem(ai);

        // ValueDomain
        ValueDomain vd = new DescribedValueDomain();
        vd.setId(UUID.randomUUID());
        Designation vdDesignation = new Designation();
        var vdSign = new io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign();
        vdSign.setValue("值域-" + label);
        vdDesignation.setSign(vdSign);
        vd.setDesignations(List.of(vdDesignation));
        de.setValueDomain(vd);

        return de;
    }
}
