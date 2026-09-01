package io.github.matrixlane.metadataregistry.web.service.datadictionary;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifiedItem;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept;
import io.github.matrixlane.metadataregistry.dataspecification.DataType;
import io.github.matrixlane.metadataregistry.dataspecification.EnumeratedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.PermissibleValue;
import io.github.matrixlane.metadataregistry.dataspecification.UnitOfMeasure;
import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.export.ExportFormat;
import io.github.matrixlane.metadataregistry.web.dto.export.datadictionary.DataDictionaryExportRequest;
import io.github.matrixlane.metadataregistry.web.dto.export.datadictionary.DataDictionaryItemExport;
import io.github.matrixlane.metadataregistry.web.repository.DataElementRepository;
import io.github.matrixlane.metadataregistry.web.repository.ValueDomainRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 数据字典导出服务
 *
 * <p>负责数据字典导出的业务编排：
 * <ul>
 *   <li>查询数据元并转换为导出 DTO</li>
 *   <li>调用 Excel/PDF 导出服务生成文件</li>
 *   <li>管理异步导出任务生命周期</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataDictionaryExportService {

    private final DataElementRepository dataElementRepository;
    private final ValueDomainRepository valueDomainRepository;
    private final DataDictionaryExcelExportService excelExportService;
    private final DataDictionaryPdfExportService pdfExportService;
    private final ObjectMapper objectMapper;

    private static final String SCHEMA_VERSION = "1.0.0";
    private static final int SYNC_EXPORT_MAX_ITEMS = 5000;
    private static final int ASYNC_EXPORT_MAX_ITEMS = 50000;
    private static final int EXPORT_TASK_TTL_DAYS = 7;
    private static final int BATCH_PAGE_SIZE = 500;

    // ─────────────────────────────────────────────────────────────────
    // 同步导出
    // ─────────────────────────────────────────────────────────────────

    /**
     * 同步导出数据字典为字节数组（Excel 或 PDF）
     */
    @Transactional(readOnly = true)
    public byte[] exportSync(DataDictionaryExportRequest request) throws IOException {
        List<DataDictionaryItemExport> items = queryItems(request.itemIds(), request.maxItems());

        if (items.size() > SYNC_EXPORT_MAX_ITEMS) {
            throw new IllegalArgumentException(
                    "同步导出记录数超过上限 " + SYNC_EXPORT_MAX_ITEMS + " 条，当前 " + items.size() + " 条，请使用异步导出。");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        if (request.format() == null) {
            throw new IllegalArgumentException("不支持的同步导出格式: null");
        }

        switch (request.format()) {
            case EXCEL -> excelExportService.exportToExcel(items, baos,
                    request.includeValueDomainDetails(), request.includeRegistrationInfo());
            case PDF -> {
                if (items.size() == 1) {
                    pdfExportService.exportSingleItemPdf(items.get(0), baos);
                } else {
                    pdfExportService.exportDataDictionaryReport(items, "数据字典报告", baos);
                }
            }
            default -> throw new IllegalArgumentException("不支持的同步导出格式: " + request.format());
        }

        return baos.toByteArray();
    }


    // ─────────────────────────────────────────────────────────────────
    // 异步导出
    // ─────────────────────────────────────────────────────────────────



    // ─────────────────────────────────────────────────────────────────
    // 任务状态查询与下载
    // ─────────────────────────────────────────────────────────────────




    // ─────────────────────────────────────────────────────────────────
    // 数据查询与转换
    // ─────────────────────────────────────────────────────────────────

    /**
     * 查询数据元列表并转换为导出 DTO
     */
    List<DataDictionaryItemExport> queryItems(List<UUID> itemIds, Integer maxItems) {
        int limit = maxItems != null ? maxItems : SYNC_EXPORT_MAX_ITEMS;
        List<UUID> idsToQuery = itemIds.stream().distinct().limit(limit).toList();

        List<DataDictionaryItemExport> results = new ArrayList<>();
        // 分批查询避免 IN 子句过大
        for (int i = 0; i < idsToQuery.size(); i += BATCH_PAGE_SIZE) {
            List<UUID> batch = idsToQuery.subList(i, Math.min(i + BATCH_PAGE_SIZE, idsToQuery.size()));
            List<DataElement> elements = dataElementRepository.findAllById(batch);
            for (DataElement de : elements) {
                results.add(toExportDto(de));
            }
        }
        return results;
    }

    DataDictionaryItemExport toExportDto(DataElement de) {
        ValueDomain vd = de.getValueDomain();
        DataElementConcept dec = de.getDataElementConcept();

        String label = ItemNamingHelper.preferredDesignation(de);
        String definition = ItemNamingHelper.preferredDefinition(de);

        // 标识符和版本
        String identifier = null;
        String version = null;
        IdentifiedItem ii = de.getIdentifiedItem();
        if (ii != null && ii.getScopedIdentifiers() != null && !ii.getScopedIdentifiers().isEmpty()) {
            ScopedIdentifier si = ii.getScopedIdentifiers().get(0);
            identifier = si.getIdentifier();
            version = si.getVersion();
        }

        // 注册状态
        RegistrationState state = null;
        if (ii instanceof AdministeredItem ai && ai.getRegistrationState() != null) {
            state = ai.getRegistrationState();
        }

        // 值域信息
        String vdLabel = vd != null ? ItemNamingHelper.preferredDesignation(vd) : null;
        String dataTypeLabel = null;
        String unitOfMeasureLabel = null;
        Integer maxCharQty = null;
        String format = null;
        List<DataDictionaryItemExport.PermissibleValueExport> pvs = Collections.emptyList();

        if (vd != null) {
            DataType dt = vd.getDataType();
            if (dt != null) {
                dataTypeLabel = ItemNamingHelper.preferredDesignation(dt);
            }
            UnitOfMeasure uom = vd.getUnitOfMeasure();
            if (uom != null) {
                unitOfMeasureLabel = ItemNamingHelper.preferredDesignation(uom);
            }
            maxCharQty = vd.getMaximumCharacterQuantity();
            format = vd.getFormat();

            if (vd instanceof EnumeratedValueDomain evd) {
                pvs = extractPermissibleValues(evd);
            }
        }

        return new DataDictionaryItemExport(
                de.getId(),
                de.getClass().getSimpleName(),
                label,
                definition,
                identifier,
                version,
                state != null ? state.getRegistrationStatus() : null,
                dec != null ? dec.getId() : null,
                dec != null ? ItemNamingHelper.preferredDesignation(dec) : null,
                vd != null ? vd.getId() : null,
                vdLabel,
                dataTypeLabel,
                unitOfMeasureLabel,
                maxCharQty,
                format,
                extractDesignations(de),
                extractDefinitions(de),
                pvs,
                ii instanceof AdministeredItem ai ? ai.getCreationDateTime() : null,
                ii instanceof AdministeredItem ai ? ai.getLastChangeDateTime() : null,
                ii instanceof AdministeredItem ai ? ai.getChangeDescription() : null,
                ii instanceof AdministeredItem ai ? ai.getExplanatoryComment() : null,
                ii instanceof AdministeredItem ai ? ai.getOrigin() : null
        );
    }

    private List<String> extractDesignations(DataElement de) {
        if (de.getDesignations() == null) return Collections.emptyList();
        return de.getDesignations().stream()
                .map(d -> d.getSign() != null ? d.getSign().getValue() : null)
                .filter(s -> s != null)
                .toList();
    }

    private List<String> extractDefinitions(DataElement de) {
        if (de.getDefinitions() == null) return Collections.emptyList();
        return de.getDefinitions().stream()
                .map(d -> d.getText() != null ? d.getText().getValue() : null)
                .filter(s -> s != null)
                .toList();
    }

    private List<DataDictionaryItemExport.PermissibleValueExport> extractPermissibleValues(EnumeratedValueDomain evd) {
        // Note: PermissibleValue access depends on the exact relationship
        // For MVP, we return empty list if no direct accessor available
        // In a full implementation, this would query the PermissibleValueRepository
        return Collections.emptyList();
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    private String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("序列化请求参数失败", e);
            return obj != null ? obj.toString() : "";
        }
    }

    private DataDictionaryExportRequest parseRequest(String json) {
        try {
            return objectMapper.readValue(json, DataDictionaryExportRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析导出请求参数", e);
        }
    }
}
