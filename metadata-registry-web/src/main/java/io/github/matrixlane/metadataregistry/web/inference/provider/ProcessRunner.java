package io.github.matrixlane.metadataregistry.web.inference.provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * 进程执行抽象：以参数数组（不经 shell）启动外部进程，便于单测替换为假实现。
 */
@FunctionalInterface
public interface ProcessRunner {

    /**
     * 启动进程。
     *
     * @param command   命令与参数列表，第一个元素为可执行文件
     * @param workingDir 进程工作目录（已存在）
     * @return 运行中的进程句柄
     */
    RunningProcess start(List<String> command, Path workingDir) throws IOException;

    /**
     * 默认实现：基于 {@link ProcessBuilder}，stderr 合并进 stdout。
     * Windows 上先经 {@link WindowsCommandResolver} 解析（.cmd/.bat shim 走 cmd /c）。
     */
    static ProcessRunner defaultRunner() {
        return (command, workingDir) -> {
            ProcessBuilder builder = new ProcessBuilder(WindowsCommandResolver.resolveForCurrentOs(command));
            builder.directory(workingDir.toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            return new RunningProcess() {
                @Override
                public OutputStream stdin() {
                    return process.getOutputStream();
                }

                @Override
                public InputStream stdout() {
                    return process.getInputStream();
                }

                @Override
                public boolean waitFor(long timeoutMillis) throws InterruptedException {
                    return process.waitFor(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
                }

                @Override
                public int exitValue() {
                    return process.exitValue();
                }

                @Override
                public void destroyForcibly() {
                    process.destroyForcibly();
                }
            };
        };
    }
}
