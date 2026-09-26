package dev.hakumi.neri;

import com.intellij.platform.dap.DebugAdapterId;

final class NeriDebugAdapter extends DebugAdapterId {
    static final NeriDebugAdapter INSTANCE = new NeriDebugAdapter();

    private NeriDebugAdapter() {
        super("neri-lldb", "Neri (LLDB)");
    }
}
