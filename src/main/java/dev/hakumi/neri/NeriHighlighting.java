package dev.hakumi.neri;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import org.jetbrains.plugins.textmate.TextMateService;

final class NeriHighlighting {
    private static final Logger LOG = Logger.getInstance(NeriHighlighting.class);

    static void reload(Project project) {
        var application = ApplicationManager.getApplication();
        application.executeOnPooledThread(() -> {
            try {
                var service = TextMateService.getInstance();
                service.ensureInitialized();
                service.reloadEnabledBundles();
                // TextMate publishes its refreshed file mappings on the event thread.
                application.invokeLater(() -> {
                    boolean registered = service.getLanguageDescriptorByFileName("probe.hk") != null;
                    String message = registered ? "Neri grammar is active; .hk file type: " + FileTypeManager.getInstance().getFileTypeByFileName("probe.hk").getName()
                            : "Neri grammar registration failed; see idea.log";
                    LOG.info(message);
                    if (project != null && !project.isDisposed()) {
                        StatusBar.Info.set(message, project);
                    }
                });
            } catch (RuntimeException error) {
                LOG.warn("Cannot reload Neri highlighting", error);
            }
        });
    }
}
