package io.github.matrixlane.metadataregistry.web.config;

import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;
import java.util.Map;

/**
 * Item 列表虚拟排序键映射。
 *
 * <p>Item 继承链上列表页（数据元/值域/概念域等）的标识符与名称不在实体表上，
 * 分别在 identified_item→scoped_identifier 与 designation(sign 内嵌）链上。
 * 前端以 {@code meta.identifier} / {@code meta.label} 作为虚拟排序键提交，
 * 这里统一改写为可执行的 JPA 属性路径；其余排序键原样透传
 * （如 Registrar.identifier、MappingScheme.name 等真实字段不受影响）。
 */
public class ItemSortPageableResolver extends PageableHandlerMethodArgumentResolver {

    /** 虚拟排序键 → JPA 属性路径。键刻意取不存在的属性名，避免与真实字段冲突。 */
    static final Map<String, String> VIRTUAL_SORT_PROPERTIES = Map.of(
            "meta.identifier", "identifiedItem.scopedIdentifiers.identifier",
            "meta.label", "designations.sign.value");

    @Override
    @NonNull
    public Pageable resolveArgument(@NonNull MethodParameter parameter,
                                    @Nullable ModelAndViewContainer mavContainer,
                                    @NonNull NativeWebRequest webRequest,
                                    @Nullable WebDataBinderFactory binderFactory) {
        return remap(super.resolveArgument(parameter, mavContainer, webRequest, binderFactory));
    }

    static Pageable remap(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }
        List<Sort.Order> orders = pageable.getSort().stream()
                .map(order -> {
                    String mapped = VIRTUAL_SORT_PROPERTIES.get(order.getProperty());
                    if (mapped == null) {
                        return order;
                    }
                    Sort.Order rewritten = new Sort.Order(order.getDirection(), mapped,
                            order.getNullHandling());
                    return order.isIgnoreCase() ? rewritten.ignoreCase() : rewritten;
                })
                .toList();
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
    }
}
