package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Item 列表虚拟排序键的服务侧执行。
 *
 * <p>前端列表页以 {@code meta.identifier} / {@code meta.label} 作为虚拟排序键提交。
 * 标识符与名称不在 Item 实体表上，分别在
 * {@code identified_item→scoped_identifier} 与 {@code designation(sign 内嵌)} 链上，
 * 且两侧均为一对多集合。两种实现路径的取舍：</p>
 *
 * <ul>
 *   <li>直接把排序键改写为集合属性路径（如 {@code designations.sign.value}）交给
 *   Spring Data 生成 join——对多集合 join 会放大结果基数，分页内容出现重复行；
 *   再叠加 {@code distinct} 虽可去重，但 SQL Server 拒绝
 *   「SELECT DISTINCT 的 ORDER BY 项不在选择列表中」（错误 145），有检索词时必现 500。</li>
 *   <li>此处改为相关标量子查询排序：{@code ORDER BY (SELECT MIN(...) FROM ... WHERE item_id = root.id)}。
 *   子查询对每行恰好返回一个排序键，不 join 集合、不放大基数、无需 distinct，
 *   SQL Server / SQLite / 达梦均支持 ORDER BY 标量子查询。</li>
 * </ul>
 *
 * <p>虚拟键之外的排序键（真实字段，如 Registrar.identifier）原样透传给 Spring Data。</p>
 *
 * <p>注意：混合虚拟键与真实键的多列排序时，虚拟键之间的相对顺序保留，
 * 真实键由 Spring Data 追加，跨组先后顺序不保证（当前前端均为单列排序）。</p>
 */
public final class ItemListSorts {

    /** 虚拟排序键集合；键刻意取不存在的属性名，避免与真实字段冲突。 */
    static final Set<String> VIRTUAL_PROPERTIES = Set.of("meta.identifier", "meta.label");

    private ItemListSorts() {
    }

    /**
     * 等价于 {@code executor.findAll(spec, pageable)}，但先把虚拟排序键从 pageable 剥离，
     * 改写为相关标量子查询的 ORDER BY（经附加的 Specification 施加）。
     *
     * @param spec 业务过滤条件，可为 null
     */
    public static <T> Page<T> findAll(JpaSpecificationExecutor<T> executor,
                                      @Nullable Specification<T> spec,
                                      Pageable pageable) {
        List<Sort.Order> virtualOrders = virtualOrders(pageable);
        if (virtualOrders.isEmpty()) {
            return executor.findAll(spec, pageable);
        }
        List<Sort.Order> remaining = pageable.getSort().stream()
                .filter(o -> !VIRTUAL_PROPERTIES.contains(o.getProperty()))
                .toList();
        Pageable sanitized = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(remaining));
        Specification<T> orderSpec = virtualOrderSpec(virtualOrders);
        Specification<T> combined = (spec == null) ? orderSpec : spec.and(orderSpec);
        return executor.findAll(combined, sanitized);
    }

    private static List<Sort.Order> virtualOrders(Pageable pageable) {
        return pageable.getSort().stream()
                .filter(o -> VIRTUAL_PROPERTIES.contains(o.getProperty()))
                .toList();
    }

    private static <T> Specification<T> virtualOrderSpec(List<Sort.Order> orders) {
        return (root, query, cb) -> {
            // count 查询（结果类型 Long）不施加排序；且部分 JPA 实现对 count 查询禁用子查询排序
            if (query == null || Long.class.equals(query.getResultType())
                    || long.class.equals(query.getResultType())) {
                return cb.conjunction();
            }
            List<jakarta.persistence.criteria.Order> jpaOrders = new ArrayList<>();
            for (Sort.Order order : orders) {
                switch (order.getProperty()) {
                    case "meta.label" -> {
                        Subquery<String> sq = query.subquery(String.class);
                        Root<Designation> d = sq.from(Designation.class);
                        sq.select(minString(cb, d.get("sign").get("value")));
                        sq.where(cb.equal(d.get("item").get("id"), root.get("id")));
                        jpaOrders.add(order.isAscending() ? cb.asc(sq) : cb.desc(sq));
                    }
                    case "meta.identifier" -> {
                        Subquery<String> sq = query.subquery(String.class);
                        Root<ScopedIdentifier> si = sq.from(ScopedIdentifier.class);
                        sq.select(minString(cb, si.get("identifier")));
                        sq.where(cb.equal(si.get("identifiedItem").get("item").get("id"), root.get("id")));
                        jpaOrders.add(order.isAscending() ? cb.asc(sq) : cb.desc(sq));
                    }
                    default -> {
                        // 已由 findAll 剥离，防御性忽略
                    }
                }
            }
            if (!jpaOrders.isEmpty()) {
                query.orderBy(jpaOrders);
            }
            return cb.conjunction();
        };
    }

    /**
     * 字符串最小值。JPA CriteriaBuilder.min 的泛型上界为 Number，字符串最小值
     * 直接依赖 SQL {@code MIN()} 的文本序（SQL Server / SQLite / 达梦均支持）。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Expression<String> minString(jakarta.persistence.criteria.CriteriaBuilder cb,
                                                Expression<String> expr) {
        return (Expression<String>) cb.min((Expression) expr);
    }
}
