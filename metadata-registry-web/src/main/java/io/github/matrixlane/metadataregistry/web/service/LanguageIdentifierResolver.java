package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 按 BCP 47 标识符解析 {@code Language_Identification} 的统一防御式入口。
 *
 * <p>来由：2026-09-25 23:59 启动崩溃事故链。历史库 {@code mdr.language_identification} 只有主键
 * 约束、没有 {@code UNIQUE(language_identifier)}，外部 INSERT 产生同标识符重复行；应用侧用 Spring
 * Data JPA 派生方法 {@code findByLanguageIdentifier} 解析，该方法返回
 * {@code Optional<LanguageIdentification>} 隐含"至多一行"，命中重复行即抛
 * {@code NonUniqueResultException}；播种侧的 {@code ApplicationRunner} 抛异常会终止 Spring 上下文
 * 刷新，Tomcat 随后优雅停机，后端 503，业务全面不可用。
 *
 * <p>DB 侧根治由迁移 {@code V20260926001__Enforce_language_identifier_unique.sql} 承担（三方言
 * 幂等 dedup + {@code CREATE UNIQUE INDEX uq_language_identification_identifier}），理由同
 * ISO/IEC 11179-3:2023 6.3.7：{@code language_identifier} 是该注册项的自然键，业务上唯一。本类
 * 是应用层的第二道防线——历史脏数据或绕过唯一索引的写入不应让业务读取直接崩掉。
 *
 * <p>语义（与 spec {@code language-identification-uniqueness} 对齐）：
 * <ul>
 *   <li>0 行 → 空结果，由调用方按既有逻辑走"不存在"分支（创建或按域规则报错）；</li>
 *   <li>1 行 → 该行，不记日志；</li>
 *   <li>多行 → 按 id（UUID）字典序最小取保留行，记 WARN（含标识符、命中行数、保留行 id），
 *       不抛异常。该口径与迁移脚本的 {@code MIN(id)} 一致，保证应用读取的行正是迁移会保留、
 *       外键会被重定向到的那一行。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LanguageIdentifierResolver {

    /**
     * id 字典序比较；null id 排在最前，保证未持久化实体也能得到确定结果而非抛 NPE。
     *
     * <p>刻意用 {@code UUID.toString()} 的字典序而非 {@link UUID#compareTo}：后者把
     * {@code mostSigBits}/{@code leastSigBits} 各按<b>有符号</b> 64 位比较，两枚 id 的符号位不同时
     * 会得出与字典序相反的次序（{@code UUID.randomUUID()} 约一半的 id 符号位为 1，故并非边角情形）。
     * 三方言的 id 均为 16 字节大端存储（SQLite BLOB / SQL Server {@code uniqueidentifier}），SQL 侧
     * {@code MIN(id)} 等价于 128 位无符号字节序，也等价于规范 UUID 文本的字典序，因此三者一致。
     */
    private static final Comparator<LanguageIdentification> BY_ID_LEXICOGRAPHIC =
            Comparator.comparing(lang -> Optional.ofNullable(lang.getId()).map(UUID::toString).orElse(""),
                    Comparator.naturalOrder());

    private final LanguageIdentificationRepository languageRepository;

    public Optional<LanguageIdentification> resolve(String languageIdentifier) {
        List<LanguageIdentification> matches = languageRepository.findAllByLanguageIdentifier(languageIdentifier);
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        if (matches.size() == 1) {
            return Optional.of(matches.get(0));
        }
        LanguageIdentification kept = matches.stream()
                .min(BY_ID_LEXICOGRAPHIC)
                .orElseThrow(() -> new IllegalStateException("命中多行但流为空: " + languageIdentifier));
        log.warn("language_identification 存在重复行：language_identifier={} 共命中 {} 行，"
                        + "按 id 字典序保留 {}；请执行 V20260926001 迁移或清理历史脏数据",
                languageIdentifier, matches.size(), kept.getId());
        return Optional.of(kept);
    }
}
