package dev.hakumi.neri;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.vfs.VirtualFile;

/** Neri files remain useful in folder/CMake workspaces without native targets. */
public final class NeriEditorListener implements FileEditorManagerListener {
    @Override public void fileOpened(FileEditorManager manager, VirtualFile file) {
        NeriNavigationSupport.install(manager.getProject(), file);
        NeriLspIntegrationProvider.ensureStarted(manager.getProject(), file);
    }

    @Override public void selectionChanged(FileEditorManagerEvent event) {
        VirtualFile file = event.getNewFile();
        if (file != null) {
            NeriNavigationSupport.install(event.getManager().getProject(), file);
            NeriLspIntegrationProvider.ensureStarted(event.getManager().getProject(), file);
        }
    }
}
