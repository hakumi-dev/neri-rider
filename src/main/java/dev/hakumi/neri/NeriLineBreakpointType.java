package dev.hakumi.neri;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.dap.xdebugger.DefaultDapEditorsProvider;
import com.intellij.xdebugger.breakpoints.XLineBreakpointTypeBase;

public final class NeriLineBreakpointType extends XLineBreakpointTypeBase {
    public NeriLineBreakpointType() {
        super("neri-line", "Neri line breakpoint", new DefaultDapEditorsProvider());
    }

    @Override public boolean canPutAt(VirtualFile file, int line, Project project) {
        return file.getName().endsWith(".hk");
    }
}
