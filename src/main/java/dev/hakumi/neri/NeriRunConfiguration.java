package dev.hakumi.neri;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.filters.RegexpFilter;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.util.execution.ParametersListUtil;
import org.jdom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import com.intellij.execution.process.NopProcessHandler;
import com.intellij.execution.process.CapturingProcessHandler;

public final class NeriRunConfiguration extends RunConfigurationBase<RunConfigurationOptions> {
    public String compiler;
    public String sources = "";
    public String workingDirectory;
    public String mode = "run";
    public String arguments = "";
    public String output = "";
    public boolean release;
    public String debugAdapter = "";

    public NeriRunConfiguration(Project project, ConfigurationFactory factory, String name) {
        super(project, factory, name);
        compiler = NeriToolchain.compiler(project);
        workingDirectory = project.getBasePath() == null ? "" : project.getBasePath();
    }

    @Override public SettingsEditor<NeriRunConfiguration> getConfigurationEditor() {
        return new NeriSettingsEditor();
    }

    @Override public void checkConfiguration() throws RuntimeConfigurationException {
        if (compiler.isBlank()) throw new RuntimeConfigurationError("Select the Neri compiler executable.");
        if (!List.of("run", "build", "check").contains(mode)) throw new RuntimeConfigurationError("Unknown Neri action.");
        try {
            Path directory = Path.of(workingDirectory);
            if (!directory.isAbsolute() || !Files.isDirectory(directory)) {
                throw new RuntimeConfigurationError("Working directory must be an existing absolute directory.");
            }
            List<String> files = ParametersListUtil.parse(sources);
            if (files.isEmpty()) throw new RuntimeConfigurationError("Select at least one .hk source file.");
            for (String file : files) {
                if (!file.endsWith(".hk") || !Files.isRegularFile(directory.resolve(file))) {
                    throw new RuntimeConfigurationError("Neri source file does not exist: " + file);
                }
            }
        } catch (java.nio.file.InvalidPathException e) {
            throw new RuntimeConfigurationError("Invalid source or working directory path.");
        }
        if (!mode.equals("run") && !arguments.isBlank()) {
            throw new RuntimeConfigurationError("Program arguments are only available for Run.");
        }
        if (!mode.equals("build") && !output.isBlank()) {
            throw new RuntimeConfigurationError("An output executable is only available for Build.");
        }
    }

    GeneralCommandLine commandLine() {
        return new GeneralCommandLine(NeriCommand.arguments(compiler, mode, sources, release, output, arguments))
            .withWorkDirectory(workingDirectory);
    }

    @Override public RunProfileState getState(Executor executor, ExecutionEnvironment environment) {
        if (DefaultDebugExecutor.EXECUTOR_ID.equals(executor.getId()) && mode.equals("run")) {
            return (ignoredExecutor, runner) -> new DefaultExecutionResult(new NopProcessHandler());
        }
        if (!DefaultRunExecutor.EXECUTOR_ID.equals(executor.getId())) return null;
        return new CommandLineState(environment) {
            {
                addConsoleFilters(new RegexpFilter(getProject(), "$FILE_PATH$:$LINE$:$COLUMN$"));
            }
            @Override protected ProcessHandler startProcess() throws ExecutionException {
                return new KillableColoredProcessHandler(commandLine());
            }
        };
    }

    @Override public void readExternal(Element element) throws com.intellij.openapi.util.InvalidDataException {
        super.readExternal(element);
        compiler = element.getAttributeValue("compiler", compiler);
        sources = element.getAttributeValue("sources", sources);
        workingDirectory = element.getAttributeValue("workingDirectory", workingDirectory);
        mode = element.getAttributeValue("mode", mode);
        arguments = element.getAttributeValue("arguments", arguments);
        output = element.getAttributeValue("output", output);
        release = Boolean.parseBoolean(element.getAttributeValue("release", "false"));
        debugAdapter = element.getAttributeValue("debugAdapter", "");
    }

    @Override public void writeExternal(Element element) throws com.intellij.openapi.util.WriteExternalException {
        super.writeExternal(element);
        element.setAttribute("compiler", compiler);
        element.setAttribute("sources", sources);
        element.setAttribute("workingDirectory", workingDirectory);
        element.setAttribute("mode", mode);
        element.setAttribute("arguments", arguments);
        element.setAttribute("output", output);
        element.setAttribute("release", Boolean.toString(release));
        element.setAttribute("debugAdapter", debugAdapter);
    }

    Path debugExecutable() {
        try {
            String identity = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(getName().getBytes(StandardCharsets.UTF_8)), 0, 12);
            String suffix = com.intellij.openapi.util.SystemInfo.isWindows ? ".exe" : "";
            return Path.of(workingDirectory).resolve(".neri/debug/config-" + identity + suffix).toAbsolutePath();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    GeneralCommandLine debugBuildCommandLine() throws ExecutionException {
        try { Files.createDirectories(debugExecutable().getParent()); }
        catch (java.io.IOException exception) { throw new ExecutionException("Cannot create Neri debug output directory.", exception); }
        return new GeneralCommandLine(NeriCommand.arguments(compiler, "build", sources, false,
            debugExecutable().toString(), "")).withWorkDirectory(workingDirectory);
    }

    String resolvedDebugAdapter() throws ExecutionException {
        if (!debugAdapter.isBlank()) return resolveDebugAdapter(debugAdapter);
        var fromPath = com.intellij.execution.configurations.PathEnvironmentVariableUtil.findInPath("lldb-dap");
        if (fromPath != null) return fromPath.getAbsolutePath();
        try {
            var output = new CapturingProcessHandler(new GeneralCommandLine("xcrun", "-f", "lldb-dap")).runProcess(10_000);
            if (!output.isTimeout() && output.getExitCode() == 0 && !output.getStdout().isBlank()) return output.getStdout().trim();
        } catch (ExecutionException ignored) { }
        throw new ExecutionException("Cannot find lldb-dap. Set its executable in the Neri Run configuration.");
    }

    static String resolveDebugAdapter(String configured) throws ExecutionException {
        String candidate = configured.trim();
        if (candidate.startsWith("~/") || candidate.startsWith("~\\")) {
            candidate = Path.of(System.getProperty("user.home"), candidate.substring(2)).toString();
        }
        boolean path = Path.of(candidate).isAbsolute() || candidate.contains("/") || candidate.contains("\\");
        if (!path) {
            var resolved = com.intellij.execution.configurations.PathEnvironmentVariableUtil.findInPath(candidate);
            if (resolved != null) return resolved.getAbsolutePath();
        } else {
            var resolved = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(resolved)
                    && (com.intellij.openapi.util.SystemInfo.isWindows || Files.isExecutable(resolved))) {
                return resolved.toString();
            }
        }
        throw new ExecutionException("LLDB DAP executable does not exist or is not executable: " + candidate);
    }
}
