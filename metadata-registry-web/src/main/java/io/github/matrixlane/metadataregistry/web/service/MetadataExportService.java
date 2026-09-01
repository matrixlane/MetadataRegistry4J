package io.github.matrixlane.metadataregistry.web.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.StewardshipRecord;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.SubmissionRecord;
import io.github.matrixlane.metadataregistry.web.dto.export.AdministeredItemExport;
import io.github.matrixlane.metadataregistry.web.dto.export.MetadataExportRequest;
import io.github.matrixlane.metadataregistry.web.dto.export.MetadataExportResult;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import org.hibernate.Hibernate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 注册元数据导出服务
 *
 * <p>支持按注册状态、组织、时间范围、Item 类型过滤，以及字段白名单筛选。
 * 支持 JSON 和 CSV 两种格式。
 */
@Service
@Transactional(readOnly = true)
public class MetadataExportService {

    private final AdministeredItemRepository repository;
    private final ObjectMapper objectMapper;

    public MetadataExportService(AdministeredItemRepository repository,
                                  ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    private static final String SCHEMA_VERSION = "1.0.0";
    private static final String EXPORT_FILTER_NAME = "exportFilter";
    private static final int AUTO_ASYNC_THRESHOLD = 1_000;
    private static final int MAX_SYNC_EXPORT_SIZE = 10_000;

    /**
     * 导出注册元数据为 JSON 字符串（同步，小数据量）
     *
     * @param request 导出请求参数
     * @return JSON 字符串
     */
    public String exportToJson(MetadataExportRequest request) throws IOException {
        MetadataExportResult result = export(request);

        ObjectMapper mapper = createFilteredMapper(request.fields());
        return mapper.writeValueAsString(result);
    }

    /**
     * 导出注册元数据（同步）
     *
     * @param request 导出请求参数
     * @return 导出结果
     */
    public MetadataExportResult export(MetadataExportRequest request) {
        List<AdministeredItem> items = queryItems(request);

        if (items.size() > MAX_SYNC_EXPORT_SIZE) {
            throw new IllegalArgumentException(
                    "同步导出结果超过最大限制 " + MAX_SYNC_EXPORT_SIZE + " 条，请缩小过滤条件或使用异步导出。" +
                    "当前匹配: " + items.size());
        }

        List<AdministeredItemExport> exports = items.stream()
                .map(this::toExport)
                .toList();

        return new MetadataExportResult(
                buildMetadata(request, exports.size()),
                exports
        );
    }

    /**
     * 判断是否应自动切换为异步导出
     */
    public boolean shouldUseAsyncExport(MetadataExportRequest request) {
        long estimated = estimateCount(request);
        return estimated > MAX_SYNC_EXPORT_SIZE;
    }

    /**
     * 估算匹配记录数（不加载全部数据）
     */
    public long estimateCount(MetadataExportRequest request) {
        Specification<AdministeredItem> spec = buildSpecification(request);
        return repository.count(spec);
    }



    /**
     * 流式导出 JSON 到输出流（用于同步大文件场景）
     *
     * @param request   导出请求参数
     * @param output    输出流
     */
    public void exportJsonToStream(MetadataExportRequest request, OutputStream output) throws IOException {
        List<AdministeredItem> items = queryItems(request);

        try (JsonGenerator generator = new JsonFactory().createGenerator(output, com.fasterxml.jackson.core.JsonEncoding.UTF8)) {
            ObjectMapper filteredMapper = createFilteredMapper(request.fields());

            generator.writeStartObject();

            MetadataExportResult.ExportMetadata metadata = buildMetadata(request, items.size());
            generator.writeFieldName("exportMetadata");
            filteredMapper.writeValue(generator, metadata);

            generator.writeArrayFieldStart("items");
            for (AdministeredItem item : items) {
                AdministeredItemExport export = toExport(item);
                filteredMapper.writeValue(generator, export);
            }
            generator.writeEndArray();
            generator.writeEndObject();
        }
    }

    /**
     * 流式导出 CSV 到输出流
     *
     * @param request   导出请求参数
     * @param output    输出流
     */
    public void exportCsvToStream(MetadataExportRequest request, OutputStream output) throws IOException {
        List<AdministeredItem> items = queryItems(request);

        try (Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            // BOM for Excel compatibility
            writer.write('\ufeff');

            // Header
            writer.write("ID,ItemType,ItemLabel,ItemDefinition,Identifier,Version,RegistrationStatus,");
            writer.write("CreatedDate,LastModifiedDate,ChangeDescription,ExplanatoryComment\n");

            // Data rows
            for (AdministeredItem ai : items) {
                AdministeredItemExport export = toExport(ai);
                writer.write(escapeCsv(export.id().toString()));
                writer.write(",");
                writer.write(escapeCsv(export.itemType()));
                writer.write(",");
                writer.write(escapeCsv(export.itemLabel()));
                writer.write(",");
                writer.write(escapeCsv(export.itemDefinition()));
                writer.write(",");
                String idStr = (export.identifiers() != null && !export.identifiers().isEmpty())
                        ? export.identifiers().get(0).identifier() : "";
                writer.write(escapeCsv(idStr));
                writer.write(",");
                String verStr = (export.identifiers() != null && !export.identifiers().isEmpty())
                        ? export.identifiers().get(0).version() : "";
                writer.write(escapeCsv(verStr));
                writer.write(",");
                String statusStr = export.registrationState() != null
                        ? export.registrationState().status().name() : "";
                writer.write(escapeCsv(statusStr));
                writer.write(",");
                writer.write(export.creationDateTime() != null ? export.creationDateTime().toString() : "");
                writer.write(",");
                writer.write(export.lastChangeDateTime() != null ? export.lastChangeDateTime().toString() : "");
                writer.write(",");
                writer.write(escapeCsv(export.changeDescription()));
                writer.write(",");
                writer.write(escapeCsv(export.explanatoryComment()));
                writer.write("\n");
            }
        }
    }



    /**
     * 查询符合条件的 AdministeredItem（含 Item 类型过滤）
     */
    List<AdministeredItem> queryItems(MetadataExportRequest request) {
        Specification<AdministeredItem> spec = buildSpecification(request);
        List<AdministeredItem> items = repository.findAll(spec);

        // In-memory item type filtering
        if (!CollectionUtils.isEmpty(request.itemTypes())) {
            Set<String> typeFilter = request.itemTypes().stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());

            items = items.stream()
                    .filter(ai -> {
                        Item item = ai.getItem();
                        if (item == null) return false;
                        Item realItem = (Item) Hibernate.unproxy(item);
                        if (realItem == null) return false;
                        String typeName = realItem.getClass().getSimpleName().toLowerCase();
                        return typeFilter.contains(typeName);
                    })
                    .toList();
        }

        return items;
    }

    /**
     * 构建查询条件
     */
    Specification<AdministeredItem> buildSpecification(MetadataExportRequest request) {
        Specification<AdministeredItem> spec = Specification.where(null);

        if (request.registrationStatus() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("registrationState").get("registrationStatus"), request.registrationStatus()));
        }

        if (request.registrationAuthorityId() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("registrationAuthority").get("id"), request.registrationAuthorityId()));
        }

        if (request.stewardshipOrganizationId() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("stewardshipRecord").get("organization").get("id"), request.stewardshipOrganizationId()));
        }

        if (request.createdAfter() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("creationDateTime"), request.createdAfter()));
        }

        if (request.createdBefore() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("creationDateTime"), request.createdBefore()));
        }

        if (request.modifiedAfter() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("lastChangeDateTime"), request.modifiedAfter()));
        }

        if (request.modifiedBefore() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("lastChangeDateTime"), request.modifiedBefore()));
        }

        return spec;
    }

    /**
     * 将 AdministeredItem 转换为导出 DTO
     */
    /**
     * 公开的条目快照导出（供联邦合并包使用）。
     */
    public AdministeredItemExport exportItem(AdministeredItem ai) {
        return toExport(ai);
    }

    AdministeredItemExport toExport(AdministeredItem ai) {
        Item item = ai.getItem();
        Item realItem = item == null ? null : (Item) Hibernate.unproxy(item);

        String itemType = realItem == null ? null : realItem.getClass().getSimpleName();
        String itemLabel = realItem == null ? null : ItemNamingHelper.preferredDesignation(realItem);
        String itemDefinition = realItem == null ? null : preferredDefinition(realItem);

        List<AdministeredItemExport.ScopedIdentifierExport> identifiers = new ArrayList<>();
        if (ai.getScopedIdentifiers() != null) {
            for (ScopedIdentifier si : ai.getScopedIdentifiers()) {
                String nsLabel = null;
                UUID nsId = null;
                if (si.getNamespace() != null) {
                    nsId = si.getNamespace().getId();
                    nsLabel = ItemNamingHelper.preferredDesignation(si.getNamespace());
                }
                identifiers.add(new AdministeredItemExport.ScopedIdentifierExport(
                        si.getIdentifier(), si.getVersion(), nsId, nsLabel));
            }
        }

        AdministeredItemExport.RegistrationStateExport stateExport = null;
        RegistrationState state = ai.getRegistrationState();
        if (state != null) {
            stateExport = new AdministeredItemExport.RegistrationStateExport(
                    state.getRegistrationStatus(),
                    state.getEffectiveDate(),
                    state.getUntilDate(),
                    state.getAdministrativeNote(),
                    state.getUnresolvedIssue(),
                    state.getAdministrativeStatus()
            );
        }

        AdministeredItemExport.RegistrationAuthorityExport raExport = null;
        RegistrationAuthority ra = ai.getRegistrationAuthority();
        if (ra != null) {
            raExport = new AdministeredItemExport.RegistrationAuthorityExport(
                    ra.getId(), organizationName(ra));
        }

        AdministeredItemExport.StewardshipExport stewardshipExport = null;
        StewardshipRecord sr = ai.getStewardshipRecord();
        if (sr != null) {
            Organization org = sr.getOrganization();
            UUID orgId = org == null ? null : org.getId();
            String orgName = org == null ? null : organizationName(org);

            var contact = sr.getContact();
            UUID contactId = contact == null ? null : contact.getId();
            String contactName = contact == null ? null : contactName(contact);

            stewardshipExport = new AdministeredItemExport.StewardshipExport(
                    orgId, orgName, contactId, contactName);
        }

        List<String> submissionOrgs = new ArrayList<>();
        if (ai.getSubmissionRecords() != null) {
            for (SubmissionRecord rec : ai.getSubmissionRecords()) {
                if (rec.getOrganization() != null) {
                    String name = organizationName(rec.getOrganization());
                    if (name != null) {
                        submissionOrgs.add(name);
                    }
                }
            }
        }

        return new AdministeredItemExport(
                ai.getId(),
                itemType,
                itemLabel,
                itemDefinition,
                identifiers,
                stateExport,
                raExport,
                stewardshipExport,
                submissionOrgs,
                ai.getCreationDateTime(),
                ai.getLastChangeDateTime(),
                ai.getChangeDescription(),
                ai.getExplanatoryComment(),
                ai.getOrigin()
        );
    }

    private MetadataExportResult.ExportMetadata buildMetadata(MetadataExportRequest request, int totalCount) {
        Map<String, Object> filters = new HashMap<>();
        if (request.registrationStatus() != null) {
            filters.put("registrationStatus", request.registrationStatus());
        }
        if (request.registrationAuthorityId() != null) {
            filters.put("registrationAuthorityId", request.registrationAuthorityId());
        }
        if (request.stewardshipOrganizationId() != null) {
            filters.put("stewardshipOrganizationId", request.stewardshipOrganizationId());
        }
        if (request.createdAfter() != null) {
            filters.put("createdAfter", request.createdAfter());
        }
        if (request.createdBefore() != null) {
            filters.put("createdBefore", request.createdBefore());
        }
        if (request.modifiedAfter() != null) {
            filters.put("modifiedAfter", request.modifiedAfter());
        }
        if (request.modifiedBefore() != null) {
            filters.put("modifiedBefore", request.modifiedBefore());
        }
        if (!CollectionUtils.isEmpty(request.itemTypes())) {
            filters.put("itemTypes", request.itemTypes());
        }
        if (!CollectionUtils.isEmpty(request.fields())) {
            filters.put("fields", request.fields());
        }

        return new MetadataExportResult.ExportMetadata(
                SCHEMA_VERSION, LocalDateTime.now(), totalCount, filters);
    }

    private ObjectMapper createFilteredMapper(List<String> fields) {
        ObjectMapper mapper = objectMapper.copy();

        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        if (!CollectionUtils.isEmpty(fields)) {
            Set<String> fieldSet = fields.stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
            // Always include id if fields are specified to ensure identity
            fieldSet.add("id");
            filterProvider.addFilter(EXPORT_FILTER_NAME,
                    SimpleBeanPropertyFilter.filterOutAllExcept(fieldSet));
        } else {
            filterProvider.addFilter(EXPORT_FILTER_NAME, SimpleBeanPropertyFilter.serializeAll());
        }
        filterProvider.setFailOnUnknownId(false);
        mapper.setFilterProvider(filterProvider);

        return mapper;
    }

    private static String preferredDefinition(Item item) {
        if (item == null || item.getDefinitions() == null || item.getDefinitions().isEmpty()) {
            return null;
        }
        for (Definition def : item.getDefinitions()) {
            if (def.getText() != null && def.getText().getValue() != null) {
                return def.getText().getValue();
            }
        }
        return null;
    }

    private static String contactName(Contact contact) {
        if (contact == null) {
            return null;
        }
        if (contact.getIndividual() != null
                && contact.getIndividual().getName() != null
                && contact.getIndividual().getName().getValue() != null) {
            return contact.getIndividual().getName().getValue();
        }
        return organizationName(contact.getOrganization());
    }

    private static String organizationName(Organization org) {
        if (org == null) {
            return null;
        }
        if (org.getName() != null && !org.getName().isEmpty()) {
            Sign s = org.getName().get(0);
            if (s != null && s.getValue() != null) {
                return s.getValue();
            }
        }
        return null;
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String toJsonString(Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return obj != null ? obj.toString() : "";
        }
    }


}
