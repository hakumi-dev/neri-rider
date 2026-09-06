package dev.hakumi.neri;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;

public final class ReloadNeriHighlightingAction extends DumbAwareAction {
    @Override
    public void actionPerformed(AnActionEvent event) {
        NeriHighlighting.reload(event.getProject());
    }
}
