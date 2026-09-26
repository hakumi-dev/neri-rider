package dev.hakumi.neri;

import com.intellij.ide.plugins.PluginDescriptorLoader;
import com.intellij.platform.dap.DapStartRequest;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.List;

public final class NeriDebugSupportTest {
    public static void main(String[] args) throws Throwable {
        var launch = NeriDapLaunchArgumentsProvider.launchArguments(
            "example", "/project/.neri/debug/example", "/project", "--name \"Neri user\"");
        var values = launch.getArguments();

        if (launch.getAdapterId() != NeriDebugAdapter.INSTANCE
                || launch.getRequest() != DapStartRequest.Launch
                || !values.get("program").equals("/project/.neri/debug/example")
                || !values.get("cwd").equals("/project")
                || !values.get("sourcePath").equals("/project")
                || !values.get("args").equals(List.of("--name", "Neri user"))) {
            throw new AssertionError("Neri debug launch must preserve executable, source root, working directory and arguments");
        }
        var build = NeriCommand.arguments("neri", "build", "main.hk", false,
            "/project/.neri/debug/example", "ignored");

        if (!build.equals(List.of("neri", "build", "main.hk", "--output", "/project/.neri/debug/example"))) {
            throw new AssertionError("Neri debug build must emit a non-release executable and never forward program arguments");
        }
        try {
            NeriRunConfiguration.resolveDebugAdapter("/path/that/does/not/exist/lldb-dap");
            throw new AssertionError("An invalid configured LLDB DAP path must fail before the debug build");
        } catch (com.intellij.execution.ExecutionException expected) { }
        var descriptor = new NeriDebugAdapterSupportProvider().createDebugAdapterDescriptor(null);

        if (descriptor.getId() != NeriDebugAdapter.INSTANCE
                || descriptor.getBreakpointsDescription().getSourceBreakpointType() != NeriLineBreakpointType.class
                || descriptor.getBreakpointsDescription().getExceptionBreakpointType() != NeriExceptionBreakpointType.class) {
            throw new AssertionError("Neri's Rider DAP provider must expose the Neri adapter and source breakpoints");
        }
        registeredDebugExtensionsLoad();
        System.out.println("Neri LLDB DAP launch, registration, construction and debug-build contracts passed.");
    }

    private static void registeredDebugExtensionsLoad() throws Throwable {
        var resource = NeriDebugSupportTest.class.getResource("/META-INF/plugin.xml");
        var resources = Path.of(resource.toURI()).getParent().getParent();
        var plugin = PluginDescriptorLoader.loadForCoreEnv(resources, "plugin.xml");

        if (plugin == null || plugin.getModuleDependencies().getModules().stream()
                .noneMatch(module -> module.getName().equals("intellij.platform.dap"))) {
            throw new AssertionError("DAP must be a required module dependency so Rider can load the debug extensions");
        }

        var extensions = plugin.getExtensions();
        var adapters = extensions.get("com.intellij.platform.dap.debugAdapterSupportProvider");
        var arguments = extensions.get("com.intellij.platform.dap.launchArgumentsProvider");
        var breakpoints = extensions.get("com.intellij.xdebugger.breakpointType");

        if (adapters == null || adapters.size() != 1 || arguments == null || arguments.size() != 1
                || breakpoints == null || breakpoints.size() != 2) {
            throw new AssertionError("Rider must resolve the DAP providers and both breakpoint types under their platform extension names");
        }

        for (var registrations : List.of(adapters, arguments, breakpoints)) {
            for (var registration : registrations) {
                var type = Class.forName(registration.implementation);
                // Rider 262 uses a private lookup to construct application-level extensions.
                var lookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
                lookup.findConstructor(type, MethodType.methodType(void.class)).invoke();
            }
        }
    }
}
