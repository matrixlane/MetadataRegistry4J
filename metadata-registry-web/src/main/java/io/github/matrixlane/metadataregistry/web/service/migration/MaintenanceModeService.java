package io.github.matrixlane.metadataregistry.web.service.migration;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 只读维护状态开关（数据库迁移期间使用）。
 *
 * <p>进程内标志：开启期间，{@code MaintenanceModeInterceptor} 拒绝一切写操作。
 * 重启后自动复位（迁移流程的导入阶段发生在重启之后，语义自洽）。
 */
@Service
public class MaintenanceModeService {

    private final AtomicBoolean active = new AtomicBoolean(false);

    public boolean isActive() {
        return active.get();
    }

    public void enable() {
        active.set(true);
    }

    public void disable() {
        active.set(false);
    }
}
