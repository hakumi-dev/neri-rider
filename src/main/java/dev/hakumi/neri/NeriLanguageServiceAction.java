package dev.hakumi.neri;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.platform.lsp.api.LspClientManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.PsiManager;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;

public final class NeriLanguageServiceAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(NeriLanguageServiceAction.class);

    static String describe(Project project, VirtualFile file) {
        var index = ProjectFileIndex.getInstance(project);
        var module = index.getModuleForFile(file);
        var psi = PsiManager.getInstance(project).findFile(file);
        return "File: " + file.getPath() + "\n  content=" + index.isInContent(file)
                + ", excluded=" + index.isExcluded(file)
                + ", module=" + (module == null ? "none" : module.getName())
                + ", type=" + file.getFileType().getName()
                + ", psi=" + (psi == null ? "none" : psi.getClass().getName())
                + ", highlighting=" + (psi != null && DaemonCodeAnalyzer.getInstance(project).isHighlightingAvailable(psi));
    }

    @Override public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        if (project == null || project.isDefault()) return;
        LspClientManager manager = LspClientManager.getInstance(project);
        StringBuilder status = new StringBuilder("Compiler: ").append(NeriToolchain.compiler(project))
                .append("\nRoot: ").append(project.getBasePath());
        for (var client : manager.getClients(NeriLspIntegrationProvider.class)) {
            status.append("\nLSP: ").append(client.getState());
        }
        if (manager.getClients(NeriLspIntegrationProvider.class).isEmpty()) status.append("\nLSP: not started");
        for (VirtualFile file : FileEditorManager.getInstance(project).getOpenFiles()) {
            if ("hk".equals(file.getExtension())) status.append("\n").append(describe(project, file));
        }
        LOG.info(status.toString());
        Messages.showInfoMessage(project, status.toString(), "Neri Language Server Status");
    }
}
