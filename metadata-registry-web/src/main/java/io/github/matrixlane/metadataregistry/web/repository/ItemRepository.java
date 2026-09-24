package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    /**
     * 轻量存在性检查：只查 item 根表，不加载联合子类对象图
     * （findById 会触发 30+ 子类表 join，超出 SQLite FROM 项上限）。
     */
    @Query("SELECT COUNT(i) > 0 FROM Item i WHERE i.id = :id")
    boolean existsItemById(@Param("id") UUID id);
}
