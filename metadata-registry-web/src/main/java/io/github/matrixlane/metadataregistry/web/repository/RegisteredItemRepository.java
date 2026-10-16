package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegisteredItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 注册项仓储（2026-10-09 表合并后由 {@code IdentifiedItemRepository} 更名改挂）。
 *
 * <p>{@code IdentifiedItem} 已由 {@code @Entity} 降为 {@code @MappedSuperclass}，
 * Spring Data 无法以其为泛型参数建仓；本仓改挂唯一实体层 {@link RegisteredItem}。</p>
 */
public interface RegisteredItemRepository extends JpaRepository<RegisteredItem, UUID> {

    Optional<RegisteredItem> findByItem(Item item);

    /**
     * 只读关联 Item 的外键：to-one 的 id 取值直接走 FK 列，不产生 join，
     * 也不会加载 Item 联合子类对象图。item 为空时返回空。
     */
    @Query("select ii.item.id from RegisteredItem ii where ii.id = :id")
    Optional<UUID> findItemIdById(@Param("id") UUID id);

    /**
     * 轻量类型探测：只取 Item 的具体持久化类，不加载联合子类对象图
     * （30+ 子类表及其关联整图 join 会超出 SQLite 单条 SQL 64 表上限）。
     * type() 生成的 CASE 只探测各子类表主键；Item 是抽象类，每行必属某个
     * 子类，CASE 不会产出 NULL 鉴别值。
     */
    @Query("select type(i) from Item i where i.id = :id")
    Class<?> findItemConcreteTypeById(@Param("id") UUID id);

    /**
     * 批量删除：按主键直接 DELETE，不逐条加载实体（避免 EAGER item 关联的
     * 联合子类全量加载）。调用方须先清理 scoped_identifier 等引用行。
     */
    @Modifying
    @Transactional
    @Query("delete from RegisteredItem ii where ii.id in :ids")
    void deleteAllByIdIn(@Param("ids") Collection<UUID> ids);

    /**
     * 批量查询条目的注册状态：[itemId, registrationStatus]。
     * 仅命中已注册（有 AdministeredItem 外壳）的条目；未注册条目不在结果中。
     */
    @Query("SELECT ai.item.id, ai.registrationState.registrationStatus " +
           "FROM AdministeredItem ai WHERE ai.item.id IN :itemIds")
    List<Object[]> findRegistrationStatusRowsByItemIds(@Param("itemIds") Collection<UUID> itemIds);
}
