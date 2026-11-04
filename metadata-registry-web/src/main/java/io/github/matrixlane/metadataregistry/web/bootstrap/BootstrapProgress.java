package io.github.matrixlane.metadataregistry.web.bootstrap;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 引导进度持有。启动期 Runner 与运行期"重试初始化"共用 {@link DatabaseBootstrapService}
 * 的同一入口，结论记录在这里，供就绪指示器、启动期其余 Runner 跳过判断与
 * 失败态查询读取。
 */
@Component
public class BootstrapProgress {

    private final AtomicReference<BootstrapOutcome> outcome =
            new AtomicReference<>(BootstrapOutcome.failed(
                    new BootstrapFailure(BootstrapStage.CONNECTION, "NOT_STARTED", "引导尚未执行")));

    public BootstrapOutcome outcome() {
        return outcome.get();
    }

    public void record(BootstrapOutcome result) {
        outcome.set(result);
    }
}
