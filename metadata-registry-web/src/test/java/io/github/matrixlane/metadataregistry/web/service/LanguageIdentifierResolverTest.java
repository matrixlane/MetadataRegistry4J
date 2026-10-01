package io.github.matrixlane.metadataregistry.web.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

/**
 * {@link LanguageIdentifierResolver} 单元测试。
 *
 * <p>覆盖多行防御三分支：0 行 → 空结果 / 1 行 → 该行且不记 WARN / 多行 → 保留 id 字典序最小行 +
 * WARN（identifier、命中行数、保留行 id）且不抛异常。
 *
 * <p>来由：2026-09-25 23:59 启动崩溃事故链——历史脏数据造成同一 {@code language_identifier}
 * 多行，JPA 派生方法 {@code findByLanguageIdentifier} 返回 {@code Optional} 隐含唯一假设，抛
 * {@code NonUniqueResultException}，ApplicationRunner 异常终止上下文刷新，后端 503。
 */
@ExtendWith(MockitoExtension.class)
class LanguageIdentifierResolverTest {

    @Mock
    private LanguageIdentificationRepository languageRepository;

    @InjectMocks
    private LanguageIdentifierResolver resolver;

    private Logger resolverLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        resolverLogger = (Logger) LoggerFactory.getLogger(LanguageIdentifierResolver.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        resolverLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        resolverLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    private List<ILoggingEvent> warnings() {
        return logAppender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .toList();
    }

    private static LanguageIdentification language(String identifier, String id) {
        LanguageIdentification lang = new LanguageIdentification();
        lang.setLanguageIdentifier(identifier);
        lang.setId(UUID.fromString(id));
        return lang;
    }

    @Test
    @DisplayName("0 行命中：返回空结果，交由调用方走创建分支，不记 WARN")
    void resolve_returnsEmptyWhenNoMatch() {
        when(languageRepository.findAllByLanguageIdentifier("id-ID")).thenReturn(List.of());

        Optional<LanguageIdentification> resolved = resolver.resolve("id-ID");

        assertThat(resolved).isEmpty();
        assertThat(warnings()).isEmpty();
    }

    @Test
    @DisplayName("1 行命中：返回该行本身，不记 WARN")
    void resolve_returnsTheRowWhenExactlyOneMatch() {
        LanguageIdentification only = language("zh-CN", "11111111-1111-1111-1111-111111111111");
        when(languageRepository.findAllByLanguageIdentifier("zh-CN")).thenReturn(List.of(only));

        Optional<LanguageIdentification> resolved = resolver.resolve("zh-CN");

        assertThat(resolved).containsSame(only);
        assertThat(warnings()).isEmpty();
    }

    @Test
    @DisplayName("多行命中：保留 id 字典序最小行，WARN 含 identifier/行数/保留行 id，不抛异常")
    void resolve_keepsLexicographicallySmallestIdAndWarns() {
        LanguageIdentification kept = language("en-US", "00000000-0000-0000-0000-000000000001");
        LanguageIdentification dropped1 = language("en-US", "ffffffff-ffff-ffff-ffff-ffffffffffff");
        LanguageIdentification dropped2 = language("en-US", "7fffffff-0000-0000-0000-000000000000");
        // 乱序注入，确保按 id 字典序而非入参顺序取保留行
        when(languageRepository.findAllByLanguageIdentifier("en-US"))
                .thenReturn(List.of(dropped1, dropped2, kept));

        AtomicReference<Optional<LanguageIdentification>> resolved = new AtomicReference<>();
        assertThatCode(() -> resolved.set(resolver.resolve("en-US"))).doesNotThrowAnyException();

        assertThat(resolved.get()).containsSame(kept);
        assertThat(warnings()).hasSize(1);
        ILoggingEvent warn = warnings().get(0);
        assertThat(warn.getFormattedMessage())
                .contains("en-US")
                .contains("3")
                .contains(kept.getId().toString());
    }

    @Test
    @DisplayName("多行命中后：后续其他 language_identifier 解析照常推进，不被中断")
    void resolve_continuesAfterMultiRowWarning() {
        LanguageIdentification dupA = language("zh-CN", "00000000-0000-0000-0000-00000000000a");
        LanguageIdentification dupB = language("zh-CN", "00000000-0000-0000-0000-00000000000b");
        LanguageIdentification en = language("en-US", "00000000-0000-0000-0000-00000000000c");
        when(languageRepository.findAllByLanguageIdentifier("zh-CN")).thenReturn(List.of(dupB, dupA));
        when(languageRepository.findAllByLanguageIdentifier("en-US")).thenReturn(List.of(en));

        assertThat(resolver.resolve("zh-CN")).containsSame(dupA);
        assertThat(resolver.resolve("en-US")).containsSame(en);
        assertThat(resolver.resolve("fr-FR")).isEmpty();
        assertThat(warnings()).hasSize(1);
    }
}
