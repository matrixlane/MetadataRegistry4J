package io.github.matrixlane.metadataregistry.web.inference;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring configuration properties for AI metadata inference.
 *
 * <p>Loads from {@code application.yml} under the prefix {@code mdr.inference}.
 *
 * <p>Example application.yml:
 * <pre>
 * mdr:
 *   inference:
 *     enabled: true
 *     provider: claude
 *     max-candidates: 3
 *     cli:
 *       timeout-seconds: 180
 *       work-dir: ${java.io.tmpdir}/mdr-inference
 * </pre>
 */
@ConfigurationProperties(prefix = "mdr.inference")
public class InferenceProperties {

    /**
     * 功能总开关。false 时所有 /api/v1/inference/** 端点返回功能未启用错误。
     */
    private boolean enabled = true;

    /**
     * 默认推断提供方：内置 Agent id（claude/codex/kimi），或 Agent 设置中的自定义 id。
     */
    private String provider = "cli";

    /**
     * 每字段候选数上限。Default: 3。
     */
    private int maxCandidates = 3;

    private final Cli cli = new Cli();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public int getMaxCandidates() {
        return maxCandidates;
    }

    public void setMaxCandidates(int maxCandidates) {
        this.maxCandidates = maxCandidates;
    }

    public Cli getCli() {
        return cli;
    }

    /**
     * 本地 Coding Agent（ACP 协议常驻进程）执行配置。
     */
    public static class Cli {

        /**
         * 自定义 ACP server 启动命令；非空时与 args 一起覆盖默认提供方
         * （{@code mdr.inference.provider} 或设置文件 defaultProvider）所指的内置 Agent。
         */
        private String command;

        /**
         * 自定义命令参数，解释为 ACP server 启动参数。
         */
        private List<String> args = new ArrayList<>();

        /**
         * 单次协作作业超时（秒）：session/prompt 等待时长，超时发送 session/cancel
         * 并计为作业失败。Default: 180。
         */
        private int timeoutSeconds = 180;

        /**
         * 常驻进程与每次作业会话目录的基路径；进程以基路径为 cwd 启动，
         * 每次作业在其下新建独立空子目录作为会话 cwd。
         * Default: ${java.io.tmpdir}/mdr-inference。
         */
        private String workDir = System.getProperty("java.io.tmpdir") + "/mdr-inference";

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public List<String> getArgs() {
            return args;
        }

        public void setArgs(List<String> args) {
            this.args = args;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public String getWorkDir() {
            return workDir;
        }

        public void setWorkDir(String workDir) {
            this.workDir = workDir;
        }
    }
}
