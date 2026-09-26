package dev.hakumi.neri;

import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XBreakpoint;

public final class NeriExceptionBreakpointType extends XBreakpointType<XBreakpoint<XBreakpointProperties>, XBreakpointProperties> {
    public NeriExceptionBreakpointType() {
        super("neri-exception", "Neri native exception breakpoint");
    }

    @Override public String getDisplayText(XBreakpoint<XBreakpointProperties> breakpoint) {
        return "Neri native exception";
    }
}
