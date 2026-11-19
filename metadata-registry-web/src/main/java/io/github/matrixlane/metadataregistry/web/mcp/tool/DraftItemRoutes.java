package io.github.matrixlane.metadataregistry.web.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.concept.ConceptRequest;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ConceptualDomainRequest;
import io.github.matrixlane.metadataregistry.web.dto.dataelement.DataElementRequest;
import io.github.matrixlane.metadataregistry.web.dto.dec.DataElementConceptRequest;
import io.github.matrixlane.metadataregistry.web.dto.dimensionality.DimensionalityRequest;
import io.github.matrixlane.metadataregistry.web.dto.measureclass.MeasureClassRequest;
import io.github.matrixlane.metadataregistry.web.dto.unitofmeasure.UnitOfMeasureRequest;
import io.github.matrixlane.metadataregistry.web.dto.valuedomain.ValueDomainRequest;
import io.github.matrixlane.metadataregistry.web.service.ConceptualDomainService;
import io.github.matrixlane.metadataregistry.web.service.DataElementConceptService;
import io.github.matrixlane.metadataregistry.web.service.DataElementService;
import io.github.matrixlane.metadataregistry.web.service.DimensionalityService;
import io.github.matrixlane.metadataregistry.web.service.MeasureClassService;
import io.github.matrixlane.metadataregistry.web.service.ObjectClassService;
import io.github.matrixlane.metadataregistry.web.service.PropertyService;
import io.github.matrixlane.metadataregistry.web.service.UnitOfMeasureService;
import io.github.matrixlane.metadataregistry.web.service.ValueDomainService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 起草/更新条目的类型路由（add-mcp-server-support）：9 类核心条目类型白名单，
 * 路由到既有 *Service.create/update——写路径铁律（设计稿 D4），
 * 正交链闭环/状态机/幂等等不变式由既有层天然保证。
 *
 * <p>payload 为业务面创建/更新 DTO 的 JSON 形态：经 Jackson 转类型后
 * 以 jakarta Validator 编程式校验（控制器层的 @Valid 在 MCP 面不经过）。
 */
@Component
public class DraftItemRoutes {

    private final Map<String, Route> routes;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public DraftItemRoutes(ObjectMapper objectMapper,
                           Validator validator,
                           ObjectClassService objectClassService,
                           PropertyService propertyService,
                           DataElementConceptService dataElementConceptService,
                           ConceptualDomainService conceptualDomainService,
                           ValueDomainService valueDomainService,
                           DataElementService dataElementService,
                           UnitOfMeasureService unitOfMeasureService,
                           MeasureClassService measureClassService,
                           DimensionalityService dimensionalityService) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        Map<String, Route> map = new LinkedHashMap<>();
        map.put("objectclass", new Route("ObjectClass", ConceptRequest.class,
                req -> objectClassService.create((ConceptRequest) req),
                (id, req) -> objectClassService.update(id, (ConceptRequest) req)));
        map.put("property", new Route("Property", ConceptRequest.class,
                req -> propertyService.create((ConceptRequest) req),
                (id, req) -> propertyService.update(id, (ConceptRequest) req)));
        map.put("dataelementconcept", new Route("DataElementConcept", DataElementConceptRequest.class,
                req -> dataElementConceptService.create((DataElementConceptRequest) req),
                (id, req) -> dataElementConceptService.update(id, (DataElementConceptRequest) req)));
        map.put("conceptualdomain", new Route("ConceptualDomain", ConceptualDomainRequest.class,
                req -> conceptualDomainService.create((ConceptualDomainRequest) req),
                (id, req) -> conceptualDomainService.update(id, (ConceptualDomainRequest) req)));
        map.put("valuedomain", new Route("ValueDomain", ValueDomainRequest.class,
                req -> valueDomainService.create((ValueDomainRequest) req),
                (id, req) -> valueDomainService.update(id, (ValueDomainRequest) req)));
        map.put("dataelement", new Route("DataElement", DataElementRequest.class,
                req -> dataElementService.create((DataElementRequest) req),
                (id, req) -> dataElementService.update(id, (DataElementRequest) req)));
        map.put("unitofmeasure", new Route("UnitOfMeasure", UnitOfMeasureRequest.class,
                req -> unitOfMeasureService.create((UnitOfMeasureRequest) req),
                (id, req) -> unitOfMeasureService.update(id, (UnitOfMeasureRequest) req)));
        map.put("measureclass", new Route("MeasureClass", MeasureClassRequest.class,
                req -> measureClassService.create((MeasureClassRequest) req),
                (id, req) -> measureClassService.update(id, (MeasureClassRequest) req)));
        map.put("dimensionality", new Route("Dimensionality", DimensionalityRequest.class,
                req -> dimensionalityService.create((DimensionalityRequest) req),
                (id, req) -> dimensionalityService.update(id, (DimensionalityRequest) req)));
        this.routes = Map.copyOf(map);
    }

    /** 单条类型路由：请求 DTO 类型 + 创建/更新调用（内部为既有 Service 方法）。 */
    public record Route(String canonicalType,
                        Class<?> requestType,
                        Function<Object, Object> creator,
                        BiFunction<UUID, Object, Object> updater) {
    }

    /** 按 itemType 解析路由（大小写/分隔符不敏感）；白名单外 → 422。 */
    public Route require(String itemType) {
        if (itemType == null || itemType.isBlank()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "缺少必填参数：itemType");
        }
        Route route = routes.get(normalize(itemType));
        if (route == null) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "不支持的条目类型：" + itemType + "（允许：" + allowedCanonicalTypes() + "）");
        }
        return route;
    }

    /** payload Map → 请求 DTO 转换 + 编程式 Bean Validation；失败 → 422。 */
    public Object convertAndValidate(Map<String, Object> payload, Route route) {
        if (payload == null || payload.isEmpty()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "缺少必填参数：payload");
        }
        Object request;
        try {
            request = objectMapper.convertValue(payload, route.requestType());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "payload 结构与 " + route.canonicalType() + " 请求不匹配：" + rootMessage(e));
        }
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("；"));
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "payload 校验失败：" + message);
        }
        return request;
    }

    public String allowedCanonicalTypes() {
        return routes.values().stream().map(Route::canonicalType).sorted().toList().toString();
    }

    private static String normalize(String itemType) {
        return itemType.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    private static String rootMessage(IllegalArgumentException e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
