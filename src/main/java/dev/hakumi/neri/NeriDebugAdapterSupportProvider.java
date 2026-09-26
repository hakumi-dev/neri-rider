package dev.hakumi.neri;

import com.intellij.execution.*;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.platform.dap.*;
import com.intellij.platform.dap.connection.*;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.Job;

public final class NeriDebugAdapterSupportProvider implements DebugAdapterSupportProvider<NeriDebugAdapter> {
    @Override public NeriDebugAdapter getAdapterId() { return NeriDebugAdapter.INSTANCE; }

    @Override public DebugAdapterDescriptor<NeriDebugAdapter> createDebugAdapterDescriptor(Project project) {
        return new DebugAdapterDescriptor<>() {
            @Override public NeriDebugAdapter getId() { return NeriDebugAdapter.INSTANCE; }

            @Override public DapBreakpointsDescription getBreakpointsDescription() {
                return new DapBreakpointsDescription(NeriLineBreakpointType.class, NeriExceptionBreakpointType.class);
            }

            @Override public Object launchDebugAdapter(ExecutionEnvironment environment, ExecutionResult result,
                                                        String sessionId,
                                                        Continuation<? super DebugAdapterHandle> continuation)
                    throws ExecutionException {
                var configuration = (NeriRunConfiguration) environment.getRunProfile();
                String adapter = configuration.resolvedDebugAdapter();
                var handler = new OSProcessHandler(configuration.debugBuildCommandLine());
                handler.setShouldDestroyProcessRecursively(true);
                var output = new DiagnosticTail(64 * 1024);
                handler.addProcessListener(new ProcessListener() {
                    @Override public void onTextAvailable(ProcessEvent event, com.intellij.openapi.util.Key outputType) {
                        output.append(event.getText());
                    }
                });
                handler.startNotify();
                long deadline = System.nanoTime() + 120_000_000_000L;
                Job job = continuation.getContext().get(Job.Key);
                while (!handler.waitFor(100)) {
                    if ((job != null && !job.isActive()) || System.nanoTime() >= deadline) {
                        handler.destroyProcess();
                        handler.waitFor(5_000);
                        if (job != null && !job.isActive()) throw new com.intellij.openapi.progress.ProcessCanceledException();
                        throw new ExecutionException("Neri debug build timed out after 120 seconds.");
                    }
                }
                if (handler.getExitCode() == null || handler.getExitCode() != 0) {
                    String details = output.text().trim();
                    throw new ExecutionException(details.isEmpty() ? "Neri debug build failed." : details);
                }
                return new CommandLineDebugAdapterHandle(
                    new GeneralCommandLine(adapter));
            }
        };
    }

    private static final class DiagnosticTail {
        private final int limit;
        private final StringBuilder text = new StringBuilder();

        DiagnosticTail(int limit) { this.limit = limit; }

        synchronized void append(String value) {
            text.append(value);
            if (text.length() > limit) text.delete(0, text.length() - limit);
        }

        synchronized String text() { return text.toString(); }
    }
}
