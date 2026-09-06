package dev.hakumi.neri;

import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.platform.lsp.api.LspClientManager;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import kotlin.Unit;

/** Refresh project membership after file-system changes, including closed sources. */
public final class NeriProjectFilesListener implements BulkFileListener {
    static boolean relevant(String root, String path, boolean directory) {
        if (!path.startsWith(root + "/")) return false;
        String relative = path.substring(root.length() + 1);
        for (String component : relative.split("/")) {
            if (component.equals("build") || component.equals(".bootstrap")
                    || component.equals(".git") || component.equals(".idea")
                    || component.equals(".neri") || component.equals(".cache")
                    || component.equals("out") || component.equals("dist")
                    || component.equals("target") || component.equals("bin")) return false;
        }
        return directory || relative.equals("neri.json") || relative.endsWith("/neri.json")
                || relative.endsWith(".hk");
    }

    @Override public void after(List<? extends VFileEvent> events) {
        for (var project : ProjectManager.getInstance().getOpenProjects()) {
            String root = project.getBasePath();
            if (root == null || project.isDisposed()) continue;
            var changes = new ArrayList<FileEvent>();
            for (var event : events) {
                boolean directory = event.getFile() != null && event.getFile().isDirectory();
                if (relevant(root, event.getPath(), directory)) {
                    var kind = event instanceof VFileDeleteEvent ? FileChangeType.Deleted
                            : event instanceof VFileCreateEvent ? FileChangeType.Created : FileChangeType.Changed;
                    changes.add(new FileEvent(Path.of(event.getPath()).toUri().toString(), kind));
                }
                if (event instanceof VFilePropertyChangeEvent property && property.isRename()) {
                    if (relevant(root, property.getOldPath(), directory)) {
                        changes.add(new FileEvent(Path.of(property.getOldPath()).toUri().toString(), FileChangeType.Deleted));
                    }
                }
                if (event instanceof VFileMoveEvent move && relevant(root, move.getOldPath(), directory)) {
                    changes.add(new FileEvent(Path.of(move.getOldPath()).toUri().toString(), FileChangeType.Deleted));
                }
            }
            if (changes.isEmpty()) continue;
            var notification = new DidChangeWatchedFilesParams(changes);
            for (var client : LspClientManager.getInstance(project).getClients(NeriLspIntegrationProvider.class)) {
                client.sendNotification(server -> {
                    server.getWorkspaceService().didChangeWatchedFiles(notification);
                    return Unit.INSTANCE;
                });
            }
        }
    }
}
