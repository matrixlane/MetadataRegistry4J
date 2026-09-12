package io.github.matrixlane.metadataregistry.web.inference.provider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Windows 命令解析：{@link ProcessBuilder} 底层走 CreateProcess，只按 PATH 补
 * {@code .exe}，无法直接启动 npm 全局安装产生的 {@code .cmd} 批处理 shim
 * （如 claude.cmd / codex.cmd）。本工具在 Windows 上按 PATH + PATHEXT 手动解析
 * 可执行文件：命中 {@code .cmd/.bat} 时包装为 {@code cmd.exe /c <完整路径> ...}，
 * 命中其它可执行文件时替换为完整路径；未命中或非 Windows 时原样返回。
 *
 * <p>已知限制：经 {@code cmd /c} 包装后参数含空格/引号时受 cmd 引号规则约束；
 * ACP server 命令行参数均为简单开关（如 {@code kimi acp}），不触发该边界。
 */
final class WindowsCommandResolver {

    private static final String DEFAULT_PATHEXT = ".COM;.EXE;.BAT;.CMD";

    private WindowsCommandResolver() {
    }

    /** 按当前操作系统解析；非 Windows 原样返回。 */
    static List<String> resolveForCurrentOs(List<String> command) {
        return resolve(command, System.getProperty("os.name", ""), System.getenv("PATH"), System.getenv("PATHEXT"));
    }

    /** 可测试入口：显式注入 osName / PATH / PATHEXT。 */
    static List<String> resolve(List<String> command, String osName, String pathEnv, String pathExt) {
        if (command == null || command.isEmpty()
                || osName == null || !osName.toLowerCase(Locale.ROOT).contains("win")) {
            return command;
        }
        String exe = command.get(0);
        if (exe.isBlank()) {
            return command;
        }
        if (exe.indexOf('/') >= 0 || exe.indexOf('\\') >= 0 || exe.indexOf(':') >= 0) {
            return isBatchScript(exe) ? wrapWithCmd(command, exe) : command;
        }
        String found = findOnPath(exe, pathEnv, pathExt);
        if (found == null) {
            return command;
        }
        List<String> resolved = new ArrayList<>(command);
        resolved.set(0, found);
        return isBatchScript(found) ? wrapWithCmd(resolved, found) : resolved;
    }

    private static String findOnPath(String exe, String pathEnv, String pathExt) {
        if (pathEnv == null || pathEnv.isBlank()) {
            return null;
        }
        List<String> extensions = new ArrayList<>();
        for (String ext : (pathExt == null || pathExt.isBlank() ? DEFAULT_PATHEXT : pathExt).split(";")) {
            String trimmed = ext.trim();
            if (!trimmed.isEmpty()) {
                extensions.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }
        // 仅接受带可执行扩展名的命中：无扩展名的同名文件（如 npm 附带的 POSIX shell
        // shim）CreateProcess 无法执行（error=193），必须跳过让 .cmd/.exe 命中
        List<String> candidates = new ArrayList<>();
        String lowerExe = exe.toLowerCase(Locale.ROOT);
        if (extensions.stream().anyMatch(lowerExe::endsWith)) {
            candidates.add(exe);
        } else {
            for (String ext : extensions) {
                candidates.add(exe + ext);
            }
        }
        for (String dir : pathEnv.split(";")) {
            if (dir.isBlank()) {
                continue;
            }
            for (String candidate : candidates) {
                Path full = Path.of(dir.trim()).resolve(candidate);
                if (Files.isRegularFile(full)) {
                    return full.toAbsolutePath().normalize().toString();
                }
            }
        }
        return null;
    }

    private static boolean isBatchScript(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".cmd") || lower.endsWith(".bat");
    }

    private static List<String> wrapWithCmd(List<String> command, String resolvedExe) {
        List<String> wrapped = new ArrayList<>(command.size() + 2);
        wrapped.add("cmd.exe");
        wrapped.add("/c");
        wrapped.add(resolvedExe);
        wrapped.addAll(command.subList(1, command.size()));
        return wrapped;
    }
}
