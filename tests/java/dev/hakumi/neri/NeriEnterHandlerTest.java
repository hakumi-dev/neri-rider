package dev.hakumi.neri;

import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.DocumentWriteAccessGuard;
import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.openapi.util.Disposer;
import com.intellij.ide.plugins.PluginDescriptorLoader;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.util.List;
import java.nio.file.Path;

public final class NeriEnterHandlerTest {
    private static List<TextEdit> edit(int line, int length, String text) {
        return List.of(new TextEdit(new Range(new Position(line, 0), new Position(line, length)), text));
    }

    private static void insertsBodyAndKeepsCaretInside() {
        var document = new DocumentImpl("# 🙂\ndef main(): Void\n", true);
        int originalCaret = document.getTextLength();
        var pending = NeriEnterHandler.Pending.capture(document, originalCaret);
        int caret = pending.apply(document, originalCaret, edit(2, 0, "  \nend"));

        if (!document.getText().endsWith("\n  \nend") || caret != originalCaret + 2) {
            throw new AssertionError("Enter must retain the caret on the indented body line");
        }
    }

    private static void rejectsLateResults() {
        var document = new DocumentImpl("if ready\n  ", true);
        int caret = document.getTextLength();
        var pending = NeriEnterHandler.Pending.capture(document, caret);
        int moved = pending.apply(document, caret - 1, edit(1, 2, "  \nend"));
        document.insertString(caret, "value");
        int changed = pending.apply(document, caret, edit(1, 2, "  \nend"));

        if (moved != -1 || changed != -1 || !document.getText().endsWith("  value")) {
            throw new AssertionError("Typing and caret movement must invalidate pending Enter edits");
        }
    }

    private static void respectsExistingCodeAndIndentation() {
        var document = new DocumentImpl("class Box\n\t\nend", true);
        var pending = NeriEnterHandler.Pending.capture(document, 11);
        int rejected = pending.apply(document, 11, edit(2, 3, "end"));
        int caret = pending.apply(document, 11, edit(1, 1, "\t"));

        if (rejected != -1 || caret != 11 || !document.getText().equals("class Box\n\t\nend")
                || NeriEnterHandler.Pending.capture(document, document.getTextLength()) != null) {
            throw new AssertionError("Enter edits must own only the blank body line");
        }
    }

    public static void main(String[] args) throws Exception {
        var lifetime = Disposer.newDisposable();
        try {
            new CoreApplicationEnvironment(lifetime);
            CoreApplicationEnvironment.registerApplicationExtensionPoint(
                    DocumentWriteAccessGuard.EP_NAME, DocumentWriteAccessGuard.class);
            insertsBodyAndKeepsCaretInside();
            rejectsLateResults();
            respectsExistingCodeAndIndentation();
            var resource = NeriEnterHandlerTest.class.getResource("/META-INF/plugin.xml");
            var plugin = PluginDescriptorLoader.loadForCoreEnv(
                    Path.of(resource.toURI()).getParent().getParent(), "plugin.xml");
            var registered = plugin.getExtensions().get("com.intellij.enterHandlerDelegate");

            if (registered == null || registered.size() != 1
                    || Class.forName(registered.getFirst().implementation).getConstructor().newInstance()
                        .getClass() != NeriEnterHandler.class) {
                throw new AssertionError("Rider must register and construct the Neri Enter handler");
            }
        } finally {
            Disposer.dispose(lifetime);
        }
        System.out.println("Neri Enter edits, caret placement and stale-response protection passed.");
    }
}
