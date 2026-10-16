package io.github.matrixlane.metadataregistry.web.repository;

import java.util.List;
import java.util.UUID;

/**
 * {@link SubmissionRecordRepository} 的自定义实现片段。
 *
 * <p>取「某受管项关联的提交记录 id」必须绕开实体关联：原 JPQL
 * {@code JOIN sr.registeredItems} 需实例化 {@code RegisteredItem}，而它是
 * {@code IdentifiedItem} 下 30+ 个 Item 子类的 JOINED 继承根，Hibernate 会把所有子类表
 * 拉进同一条 SQL 的 join。该查询又在条目列表的逐条路径上被调用（{@code buildItemMeta}），
 * 于是 N+1 乘以重 join——关联表为空时不可见，关联规模上万后列表接口数十秒级超时
 * （2026-10-09，见 openspec/changes/administered-item-reference-documents-ui）。
 *
 * <p>改为直接按关联表的 {@code registered_item_id} 列取 {@code submission_record_id}，
 * 完全不触碰继承映射；表名经 {@code DatabaseEnvironment.qualify} 限定，保持方言中立
 * （见 database-portability「业务代码方言中立」）。
 */
public interface SubmissionRecordRepositoryCustom {

    /**
     * 取指定受管项关联的全部提交记录 id，按 id 降序（与既有 JPQL 的
     * {@code ORDER BY sr.id DESC} 同序）。
     *
     * <p>不展开受管项侧的任何实体，也不加载提交记录本身。</p>
     */
    List<UUID> findSubmissionRecordIdsByRegisteredItemId(UUID registeredItemId);

    /**
     * 按一批提交记录 id 取各自关联的注册项**数量**（submissionRecordId -> count）。
     *
     * <p>供 SubmissionRecordMapper.toSummary 使用：一条提交记录可关联上万注册项
     * （基准数据补齐后为 11,182），走实体关联物化会逐条触发 Item 子类继承展开。</p>
     */
    java.util.Map<UUID, Long> countRegisteredItemsBySubmissionRecordIds(
            java.util.Collection<UUID> submissionRecordIds);
}