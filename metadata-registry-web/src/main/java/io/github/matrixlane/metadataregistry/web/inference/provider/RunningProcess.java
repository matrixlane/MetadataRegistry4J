package io.github.matrixlane.metadataregistry.web.inference.provider;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 运行中进程句柄。ACP 模式下 stdin 承载 NDJSON 协议帧（由 {@link AcpConnection}
 * 负责写入与配对读取），stdout 按行消费，无读取上限。
 */
public interface RunningProcess {

    OutputStream stdin();

    InputStream stdout();

    /**
     * 等待进程退出。
     *
     * @param timeoutMillis 超时毫秒数
     * @return true 表示正常退出；false 表示超时
     */
    boolean waitFor(long timeoutMillis) throws InterruptedException;

    int exitValue();

    void destroyForcibly();
}
