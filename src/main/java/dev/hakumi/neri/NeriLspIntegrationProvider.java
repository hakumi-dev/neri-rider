package dev.hakumi.neri;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lsp.api.LspIntegrationProvider;
import com.intellij.platform.lsp.api.LspClientDescriptor;
import com.intellij.platform.lsp.api.LspClientManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;

public final class NeriLspIntegrationProvider implements LspIntegrationProvider {
    @Override public void fileOpened(Project project, VirtualFile file, LspClientStarter starter) {
        VirtualFile root = root(project);
        if (root != null && supports(root, file)) {
            starter.ensureClientStarted(new Descriptor(project, root));
        }
    }

    static VirtualFile root(Project project) {
        return project.getBasePath() == null ? null
                : LocalFileSystem.getInstance().findFileByPath(project.getBasePath());
    }

    static boolean supports(VirtualFile root, VirtualFile file) {
        if (!file.isInLocalFileSystem() || !"hk".equals(file.getExtension())
                || !VfsUtilCore.isAncestor(root, file, true)) return false;
        String relative = VfsUtilCore.getRelativePath(file, root, '/');
        if (relative == null) return false;
        for (String excluded : new String[]{"build/", ".bootstrap/", ".git/", ".idea/"}) {
            if (relative.startsWith(excluded)) return false;
        }
        return true;
    }

    static void ensureStarted(Project project, VirtualFile file) {
        if (project.isDefault() || project.isDisposed()) return;
        VirtualFile root = root(project);
        if (root != null && supports(root, file)) {
            // Public manager API also checks TrustedProjects before launching.
            // A Neri document need not be a CMake target to have language services.
            NeriProjectSupport.ensureContent(project, file, () -> {
                if (!project.isDisposed()) LspClientManager.getInstance(project).ensureClientStarted(
                        NeriLspIntegrationProvider.class, new Descriptor(project, root));
            });
        }
    }

    private static final class Descriptor extends LspClientDescriptor {
        private final Project project;
        private final VirtualFile root;

        Descriptor(Project project, VirtualFile root) {
            super(project, "Neri", root);
            this.project = project;
            this.root = root;
        }

        @Override public boolean isSupportedFile(VirtualFile file) {
            return supports(root, file);
        }

        @Override public String getLanguageId(VirtualFile file) {
            return "neri";
        }

        @Override public GeneralCommandLine createCommandLine() {
            GeneralCommandLine command = new GeneralCommandLine(NeriToolchain.compiler(project), "lsp");
            if (project.getBasePath() != null) command.withWorkDirectory(project.getBasePath());
            return command;
        }
    }
}
