package io.github.matrixlane.metadataregistry.datasetregistration.service;

import io.github.matrixlane.metadataregistry.datasetregistration.dto.ImportResult;
import io.github.matrixlane.metadataregistry.datasetregistration.entity.InteropDataElement;
import io.github.matrixlane.metadataregistry.datasetregistration.entity.InteropDataSet;
import io.github.matrixlane.metadataregistry.datasetregistration.repository.InteropDataElementRepository;
import io.github.matrixlane.metadataregistry.datasetregistration.repository.InteropDataSetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InteropDataSetImportServiceTest {

    @Mock
    private InteropDataSetRepository dataSetRepository;

    @Mock
    private InteropDataElementRepository dataElementRepository;

    @Captor
    private ArgumentCaptor<InteropDataSet> dataSetCaptor;

    @Captor
    private ArgumentCaptor<InteropDataElement> elementCaptor;

    private InteropDataSetImportService service;

    @BeforeEach
    void setUp() {
        service = new InteropDataSetImportService(dataSetRepository, dataElementRepository);
    }

    // ── CSV Import Tests ─────────────────────────────────────────

    @Test
    void shouldImportCsvWithChineseHeaders() {
        String csv = """
                数据元标识符,数据元名称,定义,数据类型,表示格式,允许值,必填标识
                DE01.01.001,医疗机构组织机构代码,各级各类医疗机构的唯一编码,字符型,AN..20,,是
                DE01.01.002,医疗机构名称,医疗机构登记注册的名称,字符型,AN..100,,是
                """;

        when(dataSetRepository.findByCode(any())).thenReturn(Optional.empty());
        when(dataSetRepository.save(any(InteropDataSet.class))).thenAnswer(i -> {
            InteropDataSet ds = i.getArgument(0);
            ds.setId(UUID.randomUUID());
            return ds;
        });
        when(dataElementRepository.countByDataSetId(any())).thenReturn(2L);

        ImportResult result = service.importFromCsv("HDSB01.01.csv", toStream(csv));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getImportedRows()).isEqualTo(2);
        assertThat(result.getErrorRows()).isZero();
        assertThat(result.getTotalRows()).isEqualTo(2);

        verify(dataElementRepository, org.mockito.Mockito.times(2)).save(any(InteropDataElement.class));
        verify(dataSetRepository, org.mockito.Mockito.atLeast(1)).save(any(InteropDataSet.class));
    }

    @Test
    void shouldImportCsvWithEnglishHeaders() {
        String csv = """
                Data Element Identifier,Data Element Name,Definition,Data Type,Representation Format,Permissible Values,Required
                DE01.01.001,Organization Code,Unique code for healthcare org,String,AN..20,,true
                DE01.01.002,Organization Name,Registered name,String,AN..100,,true
                """;

        when(dataSetRepository.findByCode(any())).thenReturn(Optional.empty());
        when(dataSetRepository.save(any(InteropDataSet.class))).thenAnswer(i -> {
            InteropDataSet ds = i.getArgument(0);
            ds.setId(UUID.randomUUID());
            return ds;
        });
        when(dataElementRepository.countByDataSetId(any())).thenReturn(2L);

        ImportResult result = service.importFromCsv("HDSB01.01.csv", toStream(csv));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getImportedRows()).isEqualTo(2);
    }

    @Test
    void shouldReportErrorsForMissingIdentifier() {
        String csv = """
                数据元标识符,数据元名称
                ,缺少标识符的数据元
                """;

        when(dataSetRepository.findByCode(any())).thenReturn(Optional.empty());
        when(dataSetRepository.save(any(InteropDataSet.class))).thenAnswer(i -> {
            InteropDataSet ds = i.getArgument(0);
            ds.setId(UUID.randomUUID());
            return ds;
        });

        ImportResult result = service.importFromCsv("test.csv", toStream(csv));

        assertThat(result.isSuccess()).isTrue(); // Partial success
        assertThat(result.getErrorRows()).isEqualTo(1);
        assertThat(result.getImportedRows()).isZero();
        assertThat(result.getErrors()).anyMatch(e -> e.getMessage().contains("deIdentifier"));
    }

    @Test
    void shouldReportErrorsForMissingName() {
        String csv = """
                数据元标识符,数据元名称
                DE01.01.001,
                """;

        when(dataSetRepository.findByCode(any())).thenReturn(Optional.empty());
        when(dataSetRepository.save(any(InteropDataSet.class))).thenAnswer(i -> {
            InteropDataSet ds = i.getArgument(0);
            ds.setId(UUID.randomUUID());
            return ds;
        });

        ImportResult result = service.importFromCsv("test.csv", toStream(csv));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getErrorRows()).isEqualTo(1);
        assertThat(result.getImportedRows()).isZero();
        assertThat(result.getErrors()).anyMatch(e -> e.getMessage().contains("deName"));
    }

    @Test
    void shouldHandleEmptyCsv() {
        ImportResult result = service.importFromCsv("empty.csv", toStream(""));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.getMessage().contains("empty"));
    }

    @Test
    void shouldHandleCsvWithOnlyHeader() {
        String csv = "数据元标识符,数据元名称,定义\n";

        when(dataSetRepository.findByCode(any())).thenReturn(Optional.empty());
        when(dataSetRepository.save(any(InteropDataSet.class))).thenAnswer(i -> {
            InteropDataSet ds = i.getArgument(0);
            ds.setId(UUID.randomUUID());
            return ds;
        });
        when(dataElementRepository.countByDataSetId(any())).thenReturn(0L);

        ImportResult result = service.importFromCsv("header-only.csv", toStream(csv));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getImportedRows()).isZero();
    }

    @Test
    void shouldHandleCsvWithQuotedCommas() {
        String csv = """
                数据元标识符,数据元名称,允许值
                DE01.01.001,"名称,含逗号","A,B,C"
                """;

        when(dataSetRepository.findByCode(any())).thenReturn(Optional.empty());
        when(dataSetRepository.save(any(InteropDataSet.class))).thenAnswer(i -> {
            InteropDataSet ds = i.getArgument(0);
            ds.setId(UUID.randomUUID());
            return ds;
        });
        when(dataElementRepository.countByDataSetId(any())).thenReturn(1L);

        ImportResult result = service.importFromCsv("quoted.csv", toStream(csv));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getImportedRows()).isEqualTo(1);
    }

    @Test
    void shouldHandleIncrementalUpdateForExistingElement() {
        String csv = """
                数据元标识符,数据元名称,定义
                DE01.01.001,医疗机构组织机构代码,Updated definition
                """;

        UUID dataSetId = UUID.randomUUID();
        InteropDataSet ds = new InteropDataSet();
        ds.setId(dataSetId);
        ds.setCode("HDSB01.01");

        InteropDataElement existingElement = new InteropDataElement();
        existingElement.setId(UUID.randomUUID());
        existingElement.setDataSet(ds);
        existingElement.setDeIdentifier("DE01.01.001");
        existingElement.setDeName("Original Name");

        when(dataSetRepository.findByCode(any())).thenReturn(Optional.of(ds));
        when(dataElementRepository.findByDataSetIdAndDeIdentifier(dataSetId, "DE01.01.001"))
                .thenReturn(Optional.of(existingElement));
        when(dataElementRepository.countByDataSetId(dataSetId)).thenReturn(1L);

        ImportResult result = service.importFromCsv("HDSB01.01.csv", toStream(csv));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getImportedRows()).isEqualTo(1);
        // Should update existing (merge)
        verify(dataElementRepository).save(any(InteropDataElement.class));
    }

    @Test
    void shouldHandleCsvWithBom() {
        // CSV with UTF-8 BOM prefix
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        String csvContent = "数据元标识符,数据元名称\nDE01.01.001,测试名称\n";
        byte[] csvBytesUtf8 = csvContent.getBytes(StandardCharsets.UTF_8);
        byte[] csvBytes = new byte[bom.length + csvBytesUtf8.length];
        System.arraycopy(bom, 0, csvBytes, 0, bom.length);
        System.arraycopy(csvBytesUtf8, 0, csvBytes, bom.length, csvBytesUtf8.length);

        when(dataSetRepository.findByCode(any())).thenReturn(Optional.empty());
        when(dataSetRepository.save(any(InteropDataSet.class))).thenAnswer(i -> {
            InteropDataSet ds = i.getArgument(0);
            ds.setId(UUID.randomUUID());
            return ds;
        });
        when(dataElementRepository.countByDataSetId(any())).thenReturn(1L);

        ImportResult result = service.importFromCsv("bom.csv", new ByteArrayInputStream(csvBytes));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getImportedRows()).isOne();
    }

    // ── Excel Import Tests ────────────────────────────────────────

    @Test
    void shouldHandleNullInputStream() {
        ImportResult result = service.importFromExcel("test.xlsx", null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorRows()).isGreaterThanOrEqualTo(1);
    }

    // ── Helper methods ────────────────────────────────────────────

    private static InputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
