package dev.hakumi.neri;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.platform.lsp.api.LspClientManager;

public final class NeriRestartLanguageServerAction extends DumbAwareAction {
    @Override public void actionPerformed(AnActionEvent event) {
        var project = event.getProject();
        if (project == null || project.isDefault() || project.isDisposed()) return;
        LspClientManager.getInstance(project).stopAndRestartClientsIfNeeded(NeriLspIntegrationProvider.class);
        for (var file : FileEditorManager.getInstance(project).getOpenFiles()) {
            NeriLspIntegrationProvider.ensureStarted(project, file);
        }
    }
}
