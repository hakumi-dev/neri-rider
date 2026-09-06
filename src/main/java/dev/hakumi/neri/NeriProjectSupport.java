package dev.hakumi.neri;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Path;

/** A language content root, not an inferred compilation unit. */
final class NeriProjectSupport {
    private static final Logger LOG = Logger.getInstance(NeriProjectSupport.class);

    static void ensureContent(Project project, VirtualFile file, Runnable ready) {
        if (ProjectFileIndex.getInstance(project).isInContent(file)) {
            ready.run();
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;
            VirtualFile root = NeriLspIntegrationProvider.root(project);
            if (root == null || !NeriLspIntegrationProvider.supports(root, file)) return;
            WriteAction.run(() -> {
                if (ProjectFileIndex.getInstance(project).isInContent(file)) return;
                var manager = ModuleManager.getInstance(project);
                var module = manager.findModuleByName("neri-language");
                if (module == null) module = manager.newModule(
                        Path.of(root.getPath(), ".idea", "neri-language.iml").toString(), "EMPTY_MODULE");
                var model = ModuleRootManager.getInstance(module).getModifiableModel();
                try {
                    // Do not repeatedly rewrite an existing Neri root if a
                    // more specific exclusion still keeps this file outside.
                    for (var entry : model.getContentEntries()) {
                        if (entry.getUrl().equals(root.getUrl())) return;
                    }
                    var content = model.addContentEntry(root);
                    for (String excluded : new String[]{"build", ".bootstrap", ".git", ".idea"}) {
                        content.addExcludeFolder(root.getUrl() + "/" + excluded);
                    }
                    model.commit();
                } finally {
                    if (!model.isDisposed()) model.dispose();
                }
            });
            LOG.info(NeriLanguageServiceAction.describe(project, file));
            ready.run();
        });
    }
}
