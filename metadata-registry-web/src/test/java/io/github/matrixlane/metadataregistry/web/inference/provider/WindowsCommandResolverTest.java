package io.github.matrixlane.metadataregistry.web.inference.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link WindowsCommandResolver} 单测：osName/PATH/PATHEXT 均显式注入，不依赖运行平台。
 */
class WindowsCommandResolverTest {

    private static final String WIN = "Windows 11";

    @TempDir
    Path pathDir;

    @Test
    void nonWindowsPassesThrough() throws Exception {
        Files.writeString(pathDir.resolve("foo.cmd"), "@echo off");
        List<String> command = List.of("foo", "--version");
        assertThat(WindowsCommandResolver.resolve(command, "Linux", pathDir.toString(), ".CMD"))
                .isSameAs(command);
    }

    @Test
    void resolvesCmdShimViaCmdExe() throws Exception {
        Files.writeString(pathDir.resolve("codex.cmd"), "@echo off");
        List<String> resolved = WindowsCommandResolver.resolve(
                List.of("codex", "exec", "-"), WIN, pathDir.toString(), ".EXE;.CMD");
        assertThat(resolved).hasSize(5);
        assertThat(resolved.get(0)).isEqualTo("cmd.exe");
        assertThat(resolved.get(1)).isEqualTo("/c");
        assertThat(resolved.get(2).toLowerCase()).endsWith("codex.cmd");
        assertThat(resolved.subList(3, 5)).containsExactly("exec", "-");
    }

    @Test
    void resolvesExeWithoutWrapping() throws Exception {
        Files.writeString(pathDir.resolve("kimi.exe"), "MZ");
        List<String> resolved = WindowsCommandResolver.resolve(
                List.of("kimi", "-p", "{prompt}"), WIN, pathDir.toString(), ".EXE;.CMD");
        assertThat(resolved.get(0).toLowerCase()).endsWith("kimi.exe");
        assertThat(resolved).containsExactly(resolved.get(0), "-p", "{prompt}");
    }

    @Test
    void respectsPathExtOrder() throws Exception {
        Files.writeString(pathDir.resolve("foo.exe"), "MZ");
        Files.writeString(pathDir.resolve("foo.cmd"), "@echo off");
        List<String> exeFirst = WindowsCommandResolver.resolve(
                List.of("foo"), WIN, pathDir.toString(), ".EXE;.CMD");
        assertThat(exeFirst.get(0).toLowerCase()).endsWith("foo.exe");
        List<String> cmdFirst = WindowsCommandResolver.resolve(
                List.of("foo"), WIN, pathDir.toString(), ".CMD;.EXE");
        assertThat(cmdFirst.get(0)).isEqualTo("cmd.exe");
        assertThat(cmdFirst.get(2).toLowerCase()).endsWith("foo.cmd");
    }

    @Test
    void skipsExtensionlessShellShimInFavorOfCmd() throws Exception {
        Files.writeString(pathDir.resolve("agent"), "#!/bin/sh");
        Files.writeString(pathDir.resolve("agent.cmd"), "@echo off");
        List<String> resolved = WindowsCommandResolver.resolve(
                List.of("agent", "--version"), WIN, pathDir.toString(), ".EXE;.CMD");
        assertThat(resolved.get(0)).isEqualTo("cmd.exe");
        assertThat(resolved.get(2).toLowerCase()).endsWith("agent.cmd");
    }

    @Test
    void resolvesExactNameWithExtension() throws Exception {
        Files.writeString(pathDir.resolve("kimi.exe"), "MZ");
        List<String> resolved = WindowsCommandResolver.resolve(
                List.of("kimi.exe", "--version"), WIN, pathDir.toString(), ".EXE;.CMD");
        assertThat(resolved.get(0).toLowerCase()).endsWith("kimi.exe");
        assertThat(resolved.get(1)).isEqualTo("--version");
    }

    @Test
    void notFoundPassesThrough() {
        List<String> command = List.of("nonexistent-cli", "--version");
        assertThat(WindowsCommandResolver.resolve(command, WIN, pathDir.toString(), ".EXE;.CMD"))
                .isSameAs(command);
    }

    @Test
    void explicitCmdPathIsWrapped() throws Exception {
        Path script = pathDir.resolve("custom-agent.cmd");
        Files.writeString(script, "@echo off");
        List<String> resolved = WindowsCommandResolver.resolve(
                List.of(script.toString(), "-p"), WIN, "", ".CMD");
        assertThat(resolved.get(0)).isEqualTo("cmd.exe");
        assertThat(resolved.get(1)).isEqualTo("/c");
        assertThat(resolved.get(2)).isEqualTo(script.toString());
        assertThat(resolved.get(3)).isEqualTo("-p");
    }

    @Test
    void explicitMissingPathPassesThrough() {
        List<String> command = List.of(pathDir.resolve("missing.exe").toString());
        assertThat(WindowsCommandResolver.resolve(command, WIN, "", ".EXE"))
                .isSameAs(command);
    }

    @Test
    void blankOrEmptyCommandPassesThrough() {
        assertThat(WindowsCommandResolver.resolve(List.of(), WIN, "", "")).isEmpty();
        List<String> blank = List.of("  ");
        assertThat(WindowsCommandResolver.resolve(blank, WIN, pathDir.toString(), "")).isSameAs(blank);
    }
}
