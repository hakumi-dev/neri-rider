package dev.hakumi.neri;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;

import java.awt.event.MouseEvent;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Arrays;

/** Runs the platform declaration action for the keymap's declaration mouse shortcut. */
final class NeriNavigationSupport implements EditorMouseListener, EditorFactoryListener {
    private static final Key<NeriNavigationSupport> LISTENER = Key.create("neri.navigation.listener");
    private static final ConcurrentMap<Project, NeriNavigationSupport> INSTALLED = new ConcurrentHashMap<>();

    private final Project project;
    private final Disposable lifetime = Disposer.newDisposable("Neri navigation");
    private final AnAction declarationAction;
    private final ConcurrentMap<Editor, Disposable> shortcutBindings = new ConcurrentHashMap<>();

    private NeriNavigationSupport(Project project) {
        this.project = project;
        this.declarationAction = ActionManager.getInstance().getAction(NeriGotoDeclarationAction.ID);
    }

    static void install(Project project, VirtualFile file) {
        VirtualFile root = project.isDefault() || project.isDisposed() ? null
                : NeriLspIntegrationProvider.root(project);
        if (root == null || !NeriLspIntegrationProvider.supports(root, file)) return;
        NeriNavigationSupport existing = project.getUserData(LISTENER);
        if (existing != null) {
            existing.bindEditors();
            return;
        }
        NeriNavigationSupport listener = new NeriNavigationSupport(project);
        project.putUserData(LISTENER, listener);
        INSTALLED.put(project, listener);
        Disposer.register(project, listener.lifetime);
        EditorFactory.getInstance().getEventMulticaster().addEditorMouseListener(listener, listener.lifetime);
        EditorFactory.getInstance().addEditorFactoryListener(listener, listener.lifetime);
        ApplicationManager.getApplication().getMessageBus().connect(listener.lifetime).subscribe(KeymapManagerListener.TOPIC,
                new KeymapManagerListener() {
                    @Override public void activeKeymapChanged(Keymap keymap) { listener.bindEditors(); }
                    @Override public void shortcutChanged(Keymap keymap, String actionId, boolean fromSettings) {
                        if (IdeActions.ACTION_GOTO_DECLARATION.equals(actionId)) listener.bindEditors();
                    }
                });
        listener.bindEditors();
        Disposer.register(listener.lifetime, () -> {
            INSTALLED.remove(project, listener);
            project.putUserData(LISTENER, null);
        });
    }

    static void uninstall() {
        for (var entry : INSTALLED.entrySet()) {
            Disposer.dispose(entry.getValue().lifetime);
        }
        INSTALLED.clear();
    }

    @Override public void mouseClicked(EditorMouseEvent event) {
        Editor editor = event.getEditor();
        if (event.isConsumed() || event.getMouseEvent().isConsumed() || !event.isOverText()
                || editor.getProject() != project || !supports(editor)
                || !isDeclarationShortcut(event.getMouseEvent())) return;
        int offset = event.getOffset();
        if (offset < 0 || offset > editor.getDocument().getTextLength()) return;
        AnAction action = declarationAction;
        if (action == null) return;
        editor.getCaretModel().moveToOffset(offset);
        event.consume();
        ActionManager.getInstance().tryToExecute(action, event.getMouseEvent(), editor.getContentComponent(),
                ActionPlaces.MOUSE_SHORTCUT, true);
    }

    @Override public void editorCreated(EditorFactoryEvent event) {
        bindEditor(event.getEditor());
    }

    @Override public void editorReleased(EditorFactoryEvent event) {
        Disposable binding = shortcutBindings.remove(event.getEditor());
        if (binding != null) Disposer.dispose(binding);
    }

    static boolean isDeclarationShortcut(MouseEvent event) {
        return isDeclarationShortcut(KeymapManager.getInstance().getActiveKeymap(), event);
    }

    static boolean isDeclarationShortcut(Keymap keymap, MouseEvent event) {
        if (event.getClickCount() != 1) return false;
        MouseShortcut shortcut = new MouseShortcut(MouseShortcut.getButton(event), event.getModifiersEx(),
                event.getClickCount());
        return keymap.hasActionId(IdeActions.ACTION_GOTO_DECLARATION, shortcut);
    }

    private static boolean supports(Editor editor) {
        Project project = editor.getProject();
        VirtualFile file = editor.getVirtualFile();
        VirtualFile root = project == null ? null : NeriLspIntegrationProvider.root(project);
        return root != null && file != null && NeriLspIntegrationProvider.supports(root, file);
    }

    private void bindEditors() {
        if (declarationAction == null) return;
        var shortcuts = Arrays.stream(KeymapManager.getInstance().getActiveKeymap()
                        .getShortcuts(IdeActions.ACTION_GOTO_DECLARATION))
                .filter(KeyboardShortcut.class::isInstance).toArray(com.intellij.openapi.actionSystem.Shortcut[]::new);
        var shortcutSet = new CustomShortcutSet(shortcuts);
        for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
            bindEditor(editor, shortcutSet);
        }
    }

    private void bindEditor(Editor editor) {
        if (declarationAction == null) return;
        var shortcuts = Arrays.stream(KeymapManager.getInstance().getActiveKeymap()
                        .getShortcuts(IdeActions.ACTION_GOTO_DECLARATION))
                .filter(KeyboardShortcut.class::isInstance).toArray(com.intellij.openapi.actionSystem.Shortcut[]::new);
        bindEditor(editor, new CustomShortcutSet(shortcuts));
    }

    private void bindEditor(Editor editor, CustomShortcutSet shortcutSet) {
        if (editor.getProject() != project || !supports(editor)) return;
        Disposable binding = Disposer.newDisposable("Neri declaration shortcuts");
        Disposer.register(lifetime, binding);
        Disposable previous = shortcutBindings.put(editor, binding);
        if (previous != null) Disposer.dispose(previous);
        declarationAction.registerCustomShortcutSet(shortcutSet, editor.getContentComponent(), binding);
    }
}
