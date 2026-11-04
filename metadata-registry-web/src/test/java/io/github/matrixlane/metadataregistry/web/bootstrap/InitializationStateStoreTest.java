package io.github.matrixlane.metadataregistry.web.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InitializationStateStoreTest {

    @TempDir
    Path tempDir;

    private InitializationStateStore store() {
        return new InitializationStateStore(tempDir.resolve("initialization-state.yml"));
    }

    @Test
    void 记录缺失时返回空() {
        assertThat(store().read()).isEmpty();
    }

    @Test
    void 写入后可完整读回() {
        InitializationStateStore store = store();
        InitializationState state = InitializationState.fresh()
                .withDatasourceConfirmed("abc123")
                .withEdition("PERSONAL");
        store.write(state);

        Optional<InitializationState> read = store.read();
        assertThat(read).isPresent();
        assertThat(read.get().datasourceConfigured()).isTrue();
        assertThat(read.get().datasourceFingerprint()).isEqualTo("abc123");
        assertThat(read.get().initialized()).isFalse();
        assertThat(read.get().edition()).isEqualTo("PERSONAL");
        assertThat(read.get().schemaVersion()).isEqualTo(InitializationState.CURRENT_SCHEMA_VERSION);
    }

    @Test
    void 已初始化与版本记录可往返() {
        InitializationStateStore store = store();
        store.write(InitializationState.fresh()
                .withDatasourceConfirmed("fp")
                .withInitialized(true)
                .withEdition("TEAM"));

        InitializationState read = store.read().orElseThrow();
        assertThat(read.initialized()).isTrue();
        assertThat(read.edition()).isEqualTo("TEAM");
    }

    @Test
    void 文件损坏时按无记录处理且不抛异常() throws Exception {
        Path file = tempDir.resolve("initialization-state.yml");
        Files.writeString(file, "{{{{ not yaml");
        assertThat(store().read()).isEmpty();
    }

    @Test
    void 文件内容为标量时按无记录处理() throws Exception {
        Path file = tempDir.resolve("initialization-state.yml");
        Files.writeString(file, "just-a-string");
        assertThat(store().read()).isEmpty();
    }

    @Test
    void 覆盖写入不产生半截内容() {
        InitializationStateStore store = store();
        for (int i = 0; i < 50; i++) {
            store.write(InitializationState.fresh().withDatasourceConfirmed("fp-" + i));
        }
        InitializationState read = store.read().orElseThrow();
        assertThat(read.datasourceFingerprint()).isEqualTo("fp-49");
    }

    @Test
    void 记录内容不含敏感信息形状() {
        InitializationStateStore store = store();
        store.write(InitializationState.fresh().withDatasourceConfirmed("fp"));
        // 指纹是哈希，不是可逆内容；字段集合固定且无口令/连接串键。
        String raw = store.read().orElseThrow().datasourceFingerprint();
        assertThat(raw).doesNotContain("password").doesNotContain("jdbc:");
    }
}
