package dev.hakumi.neri;

import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.openapi.project.Project;
import com.intellij.platform.dap.*;
import com.intellij.util.execution.ParametersListUtil;
import java.util.LinkedHashMap;

public final class NeriDapLaunchArgumentsProvider implements DapLaunchArgumentsProvider {
    @Override public boolean isApplicable(String executorId, RunProfile profile) {
        return DefaultDebugExecutor.EXECUTOR_ID.equals(executorId)
            && profile instanceof NeriRunConfiguration configuration
            && configuration.mode.equals("run");
    }

    @Override public LaunchRequestArguments getLaunchArguments(Project project, RunProfile profile) {
        var configuration = (NeriRunConfiguration) profile;
        return launchArguments(configuration.getName(), configuration.debugExecutable().toString(),
            configuration.workingDirectory, configuration.arguments);
    }

    static LaunchRequestArguments launchArguments(String name, String program, String directory, String programArguments) {
        var arguments = new LinkedHashMap<String, Object>();
        arguments.put("name", name);
        arguments.put("type", "lldb-dap");
        arguments.put("program", program);
        arguments.put("cwd", directory);
        arguments.put("args", ParametersListUtil.parse(programArguments));
        arguments.put("sourcePath", directory);
        arguments.put("stopOnEntry", false);
        return new LaunchRequestArguments(NeriDebugAdapter.INSTANCE, DapStartRequest.Launch, arguments);
    }
}
