package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.ext.identification.ScopedIdentifierLifecycle;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierLifecycleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link ScopedIdentifierLifecycle} 卫星实体读写服务。
 *
 * <p>读:{@link #getOrDefault(UUID)} 对缺失卫星行的既存数据返回默认值
 * (versionStatus=DRAFT、creationMode=null 即 MANUAL 等价、时间戳=null),
 * 语义与字段在标准实体上时的初始值一致。
 *
 * <p>写(新建):级联创建的 SI 在调用方 save 并经 flush 后才有 ID,服务层
 * 拿不到保存点;{@link #stageNew(ScopedIdentifier, ScopedIdentifierLifecycle)}
 * 以实例为键登记待写值,事务提交前显式 flush 后统一落库
 * (afterCompletion 兜底清理,防止 ThreadLocal 泄漏)。
 *
 * <p>写(变更):唯一变更路径是版本状态流转,{@link #mutate(UUID, Consumer)}
 * 对已持久化 SI 直接 upsert。
 */
@Service
public class ScopedIdentifierLifecycleService {

    private static final ThreadLocal<Map<ScopedIdentifier, ScopedIdentifierLifecycle>> PENDING =
            ThreadLocal.withInitial(IdentityHashMap::new);

    private final ScopedIdentifierLifecycleRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    public ScopedIdentifierLifecycleService(ScopedIdentifierLifecycleRepository repository) {
        this.repository = repository;
    }

    /** 读取卫星行;缺失时返回未持久化的默认值实例(语义同字段时代的初始值)。 */
    public ScopedIdentifierLifecycle getOrDefault(UUID scopedIdentifierId) {
        if (scopedIdentifierId == null) {
            return new ScopedIdentifierLifecycle();
        }
        return repository.findByScopedIdentifierId(scopedIdentifierId)
                .orElseGet(ScopedIdentifierLifecycle::new);
    }

    /** 批量读取,返回 scopedIdentifierId → 卫星行 映射(缺失的键不在 map 中)。 */
    public Map<UUID, ScopedIdentifierLifecycle> mapByScopedIdentifierIds(Collection<UUID> scopedIdentifierIds) {
        if (scopedIdentifierIds == null || scopedIdentifierIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByScopedIdentifierIdIn(scopedIdentifierIds).stream()
                .collect(Collectors.toMap(ScopedIdentifierLifecycle::getScopedIdentifierId,
                        Function.identity()));
    }

    public ScopedIdentifierLifecycle save(ScopedIdentifierLifecycle lifecycle) {
        return repository.save(lifecycle);
    }

    /** 版本状态流转等变更路径:加载(或新建)卫星行,应用变更后落库。 */
    public ScopedIdentifierLifecycle mutate(UUID scopedIdentifierId,
                                            Consumer<ScopedIdentifierLifecycle> mutator) {
        ScopedIdentifierLifecycle lifecycle = getOrDefault(scopedIdentifierId);
        mutator.accept(lifecycle);
        if (lifecycle.getScopedIdentifierId() == null) {
            lifecycle.setScopedIdentifierId(scopedIdentifierId);
        }
        return repository.save(lifecycle);
    }

    /**
     * 登记新建 SI 的生命周期初值。SI 尚未持久化(ID 为空)时,延迟到事务
     * 提交前显式 flush 取得 ID 后落库;无活动事务(单元测试)且已有 ID 时
     * 直接保存,无 ID 则跳过。
     */
    public void stageNew(ScopedIdentifier scopedIdentifier, ScopedIdentifierLifecycle values) {
        if (scopedIdentifier.getId() != null) {
            values.setScopedIdentifierId(scopedIdentifier.getId());
            repository.save(values);
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 无事务上下文(单元测试):SI 未持久化,无从落库,跳过
            return;
        }
        Map<ScopedIdentifier, ScopedIdentifierLifecycle> pending = PENDING.get();
        if (pending.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    Map<ScopedIdentifier, ScopedIdentifierLifecycle> staged = PENDING.get();
                    if (!staged.isEmpty()) {
                        entityManager.flush();
                        staged.forEach((si, lc) -> {
                            if (si.getId() != null && lc.getId() == null) {
                                lc.setScopedIdentifierId(si.getId());
                                repository.save(lc);
                            }
                        });
                        staged.clear();
                    }
                }

                @Override
                public void afterCompletion(int status) {
                    PENDING.remove();
                }
            });
        }
        pending.put(scopedIdentifier, values);
    }
}
