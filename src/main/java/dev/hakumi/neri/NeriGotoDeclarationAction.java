package dev.hakumi.neri;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lsp.api.LspClientManager;
import com.intellij.platform.lsp.impl.LspClientImpl;
import com.intellij.platform.lsp.util.LspNavigationUtilsKt;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;

import java.awt.event.MouseEvent;

/** Runs frontend declaration navigation for Neri sources. */
public final class NeriGotoDeclarationAction extends AnAction {
    static final String ID = "Neri.GotoDeclaration";

    @Override public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project == null || editor == null || file == null) return;
        int offset = editor.getCaretModel().getOffset();
        long stamp = editor.getDocument().getModificationStamp();
        MouseEvent mouse = event.getInputEvent() instanceof MouseEvent value ? value : null;
        for (var client : LspClientManager.getInstance(project).getClients(NeriLspIntegrationProvider.class)) {
            if (!(client instanceof LspClientImpl implementation)
                    || !implementation.isFileOpened$intellij_platform_lsp_impl(file)
                    || !implementation.supportsGotoDefinition$intellij_platform_lsp_impl()) continue;
            ReadAction.nonBlocking(() -> implementation.getRequestExecutor().getElementDefinitions(file, offset).stream()
                            .map(NeriGotoDeclarationAction::location).toList())
                    .expireWith(project)
                    .expireWhen(() -> editor.isDisposed() || !file.isValid()
                            || editor.getDocument().getModificationStamp() != stamp)
                    .coalesceBy(this, editor)
                    .finishOnUiThread(ModalityState.defaultModalityState(), locations -> {
                        if (locations.isEmpty()) HintManager.getInstance().showErrorHint(editor, "Cannot find declaration");
                        else LspNavigationUtilsKt.navigateOrShowPopup(client, locations, "Declarations", mouse);
                    })
                    .submit(AppExecutorUtil.getAppExecutorService());
            return;
        }
        HintManager.getInstance().showErrorHint(editor, "Cannot find declaration");
    }

    private static Location location(LocationLink link) {
        return new Location(link.getTargetUri(), link.getTargetSelectionRange());
    }

    @Override public void update(AnActionEvent event) {
        super.update(event);
        Project project = event.getProject();
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        VirtualFile root = project == null ? null : NeriLspIntegrationProvider.root(project);
        boolean supported = root != null && file != null && NeriLspIntegrationProvider.supports(root, file);
        event.getPresentation().setEnabledAndVisible(supported);
    }
}
