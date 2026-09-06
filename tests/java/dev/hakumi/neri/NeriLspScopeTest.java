package dev.hakumi.neri;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;

public final class NeriLspScopeTest {
    private static final class File extends LightVirtualFile {
        private final VirtualFile parent;
        File(String name, VirtualFile parent) { super(name); this.parent = parent; }
        @Override public VirtualFile getParent() { return parent; }
        @Override public boolean isInLocalFileSystem() { return true; }
        @Override public String getPath() { return parent == null ? "/" + getName() : parent.getPath() + "/" + getName(); }
    }

    public static void main(String[] args) {
        File root = new File("project", null);
        File source = new File("main.hk", root);
        File generated = new File("generated.hk", new File("build", root));
        File outside = new File("main.hk", new File("project-other", null));
        if (!NeriLspIntegrationProvider.supports(root, source)
                || NeriLspIntegrationProvider.supports(root, generated)
                || NeriLspIntegrationProvider.supports(root, outside)
                || NeriLspIntegrationProvider.supports(root, new File("main.c", root))) {
            throw new AssertionError("LSP must include project Neri sources, not generated or unrelated files");
        }
        if (!NeriProjectFilesListener.relevant("/project", "/project/neri.json", false)
                || !NeriProjectFilesListener.relevant("/project", "/project/modules/library/neri.json", false)
                || !NeriProjectFilesListener.relevant("/project", "/project/src/new.hk", false)
                || !NeriProjectFilesListener.relevant("/project", "/project/src/new", true)
                || NeriProjectFilesListener.relevant("/project", "/project/build/new.hk", false)
                || NeriProjectFilesListener.relevant("/project", "/project/src/build/new.hk", false)
                || NeriProjectFilesListener.relevant("/project", "/project-other/main.hk", false)
                || NeriProjectFilesListener.relevant("/project", "/project/README.md", false)) {
            throw new AssertionError("Project refresh must include source membership changes without generated-file feedback");
        }
        System.out.println("Neri LSP root and generated-file boundaries passed.");
    }
}
